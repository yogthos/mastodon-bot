#!/usr/bin/env lumo

(ns mastodon-bot.core_test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [mastodon-bot.core :as core]
   ))

(deftest test-read-config
  (is (= 300 core/max-post-length)))

(cljs.test/run-tests)
