(ns mastodon-bot.transform_test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.spec.alpha :as s]
   [mastodon-bot.transform :as sut]
   ))

(deftest test-spec
  (is (s/valid? sut/transformations?
                []))
  (is (s/valid? sut/transformations?
                [{:source {:type :twitter-source
                           :include-replies? false
                           :include-rts? true
                           :accounts ["an-twitter-account"]} 
                  :target {:type :mastodon-target
                           :append-screen-name? true
                           :signature "my-bot"}}])))
