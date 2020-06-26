(ns mastodon-bot.transform-rss-test
  (:require
   [cljs.test :refer-macros [deftest is testing run-tests]]
   [clojure.spec.alpha :as s]
   [mastodon-bot.transform :as sut]
   ))

(def intermediate-rss-item {:created-at #inst "2020-06-26T12:17:33.000-00:00"
                            :text "Taking Theatre Online with WebGL and WebRTC\n\nhttps://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"})

(deftest should-not-resolve-urls
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00"
          :text "Taking Theatre Online with WebGL and WebRTC\n\nhttps://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"}
         (sut/intermediate-resolve-urls false intermediate-rss-item))))
