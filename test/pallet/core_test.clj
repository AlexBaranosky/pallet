(ns pallet.core-test
  (:use pallet.core)
  (require
   [pallet.utils :as utils]
   [pallet.stevedore :as stevedore]
   [pallet.resource.exec-script :as exec-script]
   [pallet.compute :as compute]
   [pallet.target :as target]
   [pallet.mock :as mock]
   [org.jclouds.compute :as jclouds]
   pallet.compute-test-utils
   [pallet.ssh-test :as ssh-test])
  (:use
   clojure.test
   pallet.test-utils
   [pallet.resource :as resource])
  (:import [org.jclouds.compute.domain NodeState OperatingSystem OsFamily]))

;; Allow running against other compute services if required
(def *compute-service* ["stub" "" "" ])

(use-fixtures
  :once
  (pallet.compute-test-utils/compute-service-fixture
   *compute-service*
   :extensions
   [(ssh-test/ssh-test-client {})]))

(deftest with-admin-user-test
  (let [x (rand)]
    (with-admin-user [x]
      (is (= x (:username pallet.utils/*admin-user*))))))

(deftest admin-user-test
  (let [username "userfred"
        old pallet.utils/*admin-user*]
    (admin-user username)
    (is (map? pallet.utils/*admin-user*))
    (is (= username (:username pallet.utils/*admin-user*)))
    (is (= (pallet.utils/default-public-key-path)
           (:public-key-path pallet.utils/*admin-user*)))
    (is (= (pallet.utils/default-private-key-path)
           (:private-key-path pallet.utils/*admin-user*)))
    (is (nil? (:password pallet.utils/*admin-user*)))

    (admin-user username :password "pw" :public-key-path "pub"
                :private-key-path "pri")
    (is (map? pallet.utils/*admin-user*))
    (is (= username (:username pallet.utils/*admin-user*)))
    (is (= "pub" (:public-key-path pallet.utils/*admin-user*)))
    (is (= "pri" (:private-key-path pallet.utils/*admin-user*)))
    (is (= "pw" (:password pallet.utils/*admin-user*)))

    (admin-user old)
    (is (= old pallet.utils/*admin-user*))))


(deftest add-prefix-to-node-type-test
  (is (= {:tag :pa} (add-prefix-to-node-type "p" {:tag :a}))))

(deftest add-prefix-to-node-map-test
  (is (= {{:tag :pa} 1} (add-prefix-to-node-map "p" {{:tag :a} 1}))))

(deftest node-count-difference-test
  (is (= { {:tag :a} 1 {:tag :b} -1}
         (node-count-difference
          { {:tag :a} 2 {:tag :b} 0}
          [(compute/make-node "a") (compute/make-node "b")])))
  (is (= { {:tag :a} 1 {:tag :b} 1}
         (node-count-difference { {:tag :a} 1 {:tag :b} 1} []))))

(deftest augment-template-from-node-test
  (defnode a [:ubuntu])
  (defnode b [])
  (let [n1 (compute/make-node "n1")]
    (is (= {:tag :a :image [:ubuntu :ubuntu] :phases nil}
           (:node-type
            ((augment-template-from-node identity)
             {:target-node n1 :node-type a})))))
  (let [n1 (compute/make-node
            "n1"
            :operating-system (OperatingSystem.
                               OsFamily/UBUNTU
                               "Ubuntu"
                               "Some version"
                               "Some arch"
                               "Desc"
                               true))]
    (is (= {:tag :a :image [:ubuntu :ubuntu] :phases nil}
           (:node-type
            ((augment-template-from-node identity)
             {:target-node n1 :node-type a}))))
    (is (= {:tag :b :image [:ubuntu] :phases nil}
           (:node-type
            ((augment-template-from-node identity)
             {:target-node n1 :node-type b}))))))

(deftest converge-node-counts-test
  (defnode a [:ubuntu])
  (let [a-node (compute/make-node "a" :state NodeState/RUNNING)]
    (converge-node-counts nil {a 1} [a-node] {}))
  (mock/expects [(org.jclouds.compute/run-nodes
                  [tag n template compute]
                  (mock/once
                   (is (= n 1))))
                 (org.jclouds.compute/build-template
                  [compute & options]
                  (mock/once :template))]
    (let [a-node (compute/make-node "a" :state NodeState/TERMINATED)]
      (converge-node-counts nil {a 1} [a-node] {}))))

(deftest nodes-in-map-test
  (defnode a [:ubuntu])
  (defnode b [:ubuntu])
  (let [a-node (compute/make-node "a")
        b-node (compute/make-node "b")
        nodes [a-node b-node]]
    (is (= [a-node]
           (nodes-in-map {a 1} nodes)))
    (is (= [a-node b-node]
           (nodes-in-map {a 1 b 2} nodes)))))

(deftest nodes-in-set-test
  (defnode a [:ubuntu])
  (defnode b [:ubuntu])
  (defnode pa [:ubuntu])
  (defnode pb [:ubuntu])
  (let [a-node (compute/make-node "a")
        b-node (compute/make-node "b")]
    (is (= {a #{a-node}}
           (nodes-in-set {a a-node} nil nil)))
    (is (= {a #{a-node b-node}}
           (nodes-in-set {a #{a-node b-node}} nil nil)))
    (is (= {a #{a-node} b #{b-node}}
           (nodes-in-set {a #{a-node} b #{b-node}} nil nil))))
  (let [a-node (compute/make-node "a")
        b-node (compute/make-node "b")]
    (is (= {pa #{a-node}}
           (nodes-in-set {a a-node} "p" nil)))
    (is (= {pa #{a-node b-node}}
           (nodes-in-set {a #{a-node b-node}} "p" nil)))
    (is (= {pa #{a-node} pb #{b-node}}
           (nodes-in-set {a #{a-node} b #{b-node}} "p" nil)))))

(deftest node-in-types?-test
  (defnode a [])
  (defnode b [])
  (is (node-in-types? [a b] (compute/make-node "a")))
  (is (not (node-in-types? [a b] (compute/make-node "c")))))

(deftest nodes-for-type-test
  (defnode a [])
  (defnode b [])
  (let [na (compute/make-node "a")
        nb (compute/make-node "b")
        nc (compute/make-node "c")]
    (is (= [nb] (nodes-for-type [na nb nc] b)))
    (is (= [na] (nodes-for-type [na nc] a)))))

(deftest nodes-in-map-test
  (defnode a [])
  (defnode b [])
  (defnode c [])
  (let [na (compute/make-node "a")
        nb (compute/make-node "b")]
    (is (= [na nb] (nodes-in-map {a 1 b 1 c 1} [na nb])))
    (is (= [na] (nodes-in-map {a 1 c 1} [na nb])))))

(deftest compute-service-and-options-test
  (binding [org.jclouds.compute/*compute* :compute
            pallet.core/*middleware* :middleware]
    (testing "defaults"
      (is (= [:compute nil 'a nil '() {:user utils/*admin-user*} :middleware]
               (compute-service-and-options 'a []))))
    (testing "passing a prefix"
      (is (= [:compute "prefix" 'a nil '() {:user utils/*admin-user*}
              :middleware]
               (compute-service-and-options "prefix" ['a]))))
    (testing "passing a user"
      (let [user (utils/make-user "fred")]
        (is (= [:compute "prefix" 'a nil '() {:user user} :middleware]
                 (compute-service-and-options "prefix" ['a user])))))
    (testing "passing a node map"
      (let [user (utils/make-user "fred")]
        (is (= [:compute "prefix" 'a {'a 'b} '() {:user user} :middleware]
                 (compute-service-and-options "prefix" ['a {'a 'b} user])))))))

(resource/defresource test-component
  (test-component-fn
   [request arg & options]
   (str arg)))

(deftest make-node-test
  (is (= {:tag :fred :image [:ubuntu] :phases nil}
         (make-node "fred" [:ubuntu])))
  (is (= {:tag :tom :image [:centos] :phases nil}
         (make-node "tom" [:centos]))))

(deftest defnode-test
  (defnode fred [:ubuntu])
  (is (= {:tag :fred :image [:ubuntu] :phases nil} fred))
  (defnode tom "This is tom" [:centos])
  (is (= {:tag :tom :image [:centos] :phases nil} tom))
  (is (= "This is tom" (:doc (meta #'tom))))
  (defnode harry (tom :image))
  (is (= {:tag :harry :image [:centos] :phases nil} harry))
  (defnode with-phases (tom :image)
    :bootstrap (resource/phase (test-component :a))
    :configure (resource/phase (test-component :b)))
  (is (= #{:bootstrap :configure} (set (keys (with-phases :phases)))))
  (let [request {:target-node (compute/make-node "tag" :id "id")
                 :target-id :id
                 :node-type with-phases}]
    (is (= ":a\n"
           (first
            (resource/produce-phases
             [:bootstrap]
             (resource-invocations (assoc request :phase :bootstrap))))))
    (is (= ":b\n"
           (first
            (resource/produce-phases
             [:configure]
             (resource-invocations (assoc request :phase :configure))))))))

(defresource identity-resource
  (identity-resource* [request x] x))

(deflocal identity-local-resource
  (identity-local-resource* [request] request))

(deftest produce-init-script-test
  (is (= "a\n"
         (produce-init-script
          {:node-type {:image []
                       :phases {:bootstrap (phase (identity-resource "a"))}}
           :target-id :id})))
  (testing "rejects local resources"
    (is (thrown?
         clojure.contrib.condition.Condition
         (produce-init-script
          {:node-type
           {:image []
            :phases {:bootstrap (phase (identity-local-resource))}}
           :target-id :id})))))


(let [seen (atom nil)]
  (defn seen? [] @seen)
  (deflocal localf
    (localf*
     [request]
     (reset! seen true)
     (is (:target-node request))
     (is (:node-type request))
     request))

  (deftest lift-test
    (defnode x [])
    (is (.contains
         "bin"
         (with-no-compute-service
           (with-admin-user (assoc utils/*admin-user* :no-sudo true)
             (with-out-str
               (lift {x (compute/make-unmanaged-node "x" "localhost")}
                     (phase (exec-script/exec-script (ls "/")))
                     (phase (localf))))))))
    (is (seen?))))

(deftest lift*-nodes-binding-test
  (defnode a [])
  (defnode b [])
  (let [na (compute/make-node "a")
        nb (compute/make-node "b")
        nc (compute/make-node "c" :state NodeState/TERMINATED)]
    (mock/expects [(apply-phase
                    [compute wrapper nodes request]
                    (do
                      (is (= #{na nb} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))))]
                  (lift* nil "" {a #{na nb nc}} nil [:configure]
                         {:user utils/*admin-user*} *middleware*))
    (mock/expects [(apply-phase
                    [compute wrapper nodes request]
                    (do
                      (is (= #{na nb} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))))]
                  (lift* nil "" {a #{na} b #{nb}} nil [:configure]
                         {:user utils/*admin-user*} *middleware*))))

(deftest lift-multiple-test
  (defnode a [])
  (defnode b [])
  (let [na (compute/make-node "a")
        nb (compute/make-node "b")
        nc (compute/make-node "c")]
    (mock/expects [(org.jclouds.compute/nodes-with-details [_] [na nb nc])
                   (apply-phase
                    [compute wrapper nodes request]
                    (do
                      (is (= #{na nb nc} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))))]
                  (binding [jclouds/*compute* :dummy]
                    (lift [a b] :configure)))))

(deftest converge*-nodes-binding-test
  (defnode a [])
  (defnode b [])
  (let [na (compute/make-node "a")
        nb (compute/make-node "b")
        nc (compute/make-node "b" :state NodeState/TERMINATED)]
    (mock/expects [(apply-phase
                    [compute wrapper nodes request]
                    (do
                      (is (= #{na nb} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))))
                   (org.jclouds.compute/nodes-with-details [& _] [na nb nc])]
                  (converge*
                   nil "" {a 1 b 1} nil [:configure] {} *middleware*))))

(deftest converge-test
  (pallet.compute-test-utils/purge-compute-service)
  (converge {(make-node "a" []) 1})
  (is (= 1 (count (org.jclouds.compute/nodes)))))