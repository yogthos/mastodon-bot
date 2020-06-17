(ns mastodon-bot.rss-api-test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.spec.alpha :as s]
   [mastodon-bot.rss-api :as sut]
   ))

(deftest test-spec
  (is (s/valid? sut/rss-source?
                {:feeds [["correctiv-blog" "https://news.correctiv.org/news/rss.php"]]}
                )))
