(ns pallet.crate.ssh-key-test
  (:use pallet.crate.ssh-key)
  (:require [pallet.template :as template]
            [pallet.resource :as resource]
            [pallet.stevedore :as stevedore]
            [pallet.utils :as utils]
            [pallet.resource.directory :as directory]
            [pallet.resource.exec-script :as exec-script]
            [pallet.resource.file :as file]
            [pallet.resource.remote-file :as remote-file]
            [clojure.string :as string])
  (:use clojure.test
        pallet.test-utils))

(use-fixtures :once with-ubuntu-script-template)


(deftest authorize-key-test
  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys"
            :owner "fred" :mode "644")
           (exec-script/exec-checked-script
            "authorize-key"
            (var auth_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys")
            (if-not (fgrep (quoted "key1") @auth_file)
              (echo (quoted "key1") ">>" @auth_file)))))
         (first
          (resource/build-resources
           []
           (authorize-key "fred" "key1"))))))

(deftest install-key-test
  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (remote-file/remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id"
            :content "private" :owner "fred" :mode "600")
           (remote-file/remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id.pub"
            :content "public" :owner "fred" :mode "644")))
         (first
          (resource/build-resources
           [] (install-key "fred" "id" "private" "public")))))
  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (remote-file/remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id"
            :content "private" :owner "fred" :mode "600")
           (remote-file/remote-file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id.pub"
            :content "public" :owner "fred" :mode "644")))
         (first
          (resource/build-resources
           []
           (install-key "fred" "id" "private" "public"))))))

(deftest generate-key-test
  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh"
            :owner "fred" :mode "755")
           (exec-script/exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa")
            (if-not (file-exists? @key_path)
              (ssh-keygen
               -f @key_path -t rsa -N "\"\"" -C "\"generated by pallet\"")))
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa"
            :owner "fred" :mode "0600")
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_rsa.pub"
            :owner "fred" :mode "0644")))
         (first
          (resource/build-resources
           []
           (generate-key "fred")))))

  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh"
            :owner "fred" :mode "755")
           (exec-script/exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa")
            (if-not (file-exists? @key_path)
              (ssh-keygen -f @key_path -t dsa -N "\"\"" -C "\"generated by pallet\"")))
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa"
            :owner "fred" :mode "0600")
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub"
            :owner "fred" :mode "0644")))
         (first
          (resource/build-resources
           [] (generate-key "fred" :type "dsa")))))

  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh"
            :owner "fred" :mode "755")
           (exec-script/exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/identity")
            (if-not (file-exists? @key_path)
              (ssh-keygen
               -f @key_path -t rsa1 -N "\"\"" -C "\"generated by pallet\"")))
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/identity"
            :owner "fred" :mode "0600")
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/identity.pub"
            :owner "fred" :mode "0644")))
         (first
          (resource/build-resources
           [] (generate-key "fred" :type "rsa1")))))

  (is (= (first
          (resource/build-resources
           []
           (exec-script/exec-checked-script
            "ssh-keygen"
            (var key_path "$(getent passwd fred | cut -d: -f6)/.ssh/c")
            (if-not (file-exists? @key_path)
              (ssh-keygen
               -f @key_path -t rsa1 -N "\"abc\""  -C "\"my comment\"")))
           (file/file "$(getent passwd fred | cut -d: -f6)/.ssh/c"
                      :owner "fred" :mode "0600")
           (file/file "$(getent passwd fred | cut -d: -f6)/.ssh/c.pub"
                      :owner "fred" :mode "0644")))
         (first
          (resource/build-resources
           []
           (generate-key
            "fred" :type "rsa1" :file "c" :no-dir true
            :comment "my comment" :passphrase "abc"))))))

(deftest authorize-key-for-localhost-test
  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd fred | cut -d: -f6)/.ssh/"
            :owner "fred" :mode "755")
           (file/file
            "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys"
            :owner "fred" :mode "644")
           (exec-script/exec-checked-script
            "authorize-key"
            (var key_file "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub")
            (var auth_file
                 "$(getent passwd fred | cut -d: -f6)/.ssh/authorized_keys")
            (if-not (grep (quoted @(cat @key_file)) @auth_file)
              (do
                (echo -n (quoted "from=\\\"localhost\\\" ") ">>" @auth_file)
                (cat @key_file ">>" @auth_file))))))
         (first
          (resource/build-resources
           []
           (authorize-key-for-localhost "fred" "id_dsa.pub")))))

  (is (= (first
          (resource/build-resources
           []
           (directory/directory
            "$(getent passwd tom | cut -d: -f6)/.ssh/"
            :owner "tom" :mode "755")
           (file/file
            "$(getent passwd tom | cut -d: -f6)/.ssh/authorized_keys"
            :owner "tom" :mode "644")
           (exec-script/exec-checked-script
            "authorize-key"
            (var key_file "$(getent passwd fred | cut -d: -f6)/.ssh/id_dsa.pub")
            (var auth_file
                 "$(getent passwd tom | cut -d: -f6)/.ssh/authorized_keys")
            (if-not (grep (quoted @(cat @key_file)) @auth_file)
              (do
                (echo -n (quoted "from=\\\"localhost\\\" ") ">>" @auth_file)
                (cat @key_file ">>" @auth_file))))))
         (first
          (resource/build-resources
           []
           (authorize-key-for-localhost
            "fred" "id_dsa.pub" :authorize-for-user "tom"))))))

(deftest invoke-test
  (is (resource/build-resources
       []
       (authorize-key "user" "pk")
       (authorize-key-for-localhost "user" "pk")
       (install-key "user" "name" "pk" "pubk")
       (generate-key "user"))))
