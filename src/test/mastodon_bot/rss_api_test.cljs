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

(def reddit-feed-item {:title "Datahike release 0.3.1"
                       :link
                       "https://www.reddit.com/r/Clojure/comments/hfxotu/datahike_release_031/"
                       :pubDate "2020-06-26T00:36:48.000Z"
                       :author "/u/yogthos"
                       :content
                       "&#32; submitted by &#32; <a href=\"https://www.reddit.com/user/yogthos\"> /u/yogthos </a> <br/> <span><a href=\"https://github.com/replikativ/datahike/releases/tag/v0.3.1\">[link]</a></span> &#32; <span><a href=\"https://www.reddit.com/r/Clojure/comments/hfxotu/datahike_release_031/\">[comments]</a></span>"
                       :contentSnippet "submitted by    /u/yogthos   [link]   [comments]"
                       :id "t3_hfxotu"
                       :isoDate "2020-06-26T00:36:48.000Z"})

(def hnrss-org-feed-item {:creator "seacaster"
                          :isoDate "2020-06-26T12:17:33.000Z"
                          :content
                          "\n<p>Article URL: <a href=\"https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/\">https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/</a></p>\n<p>Comments URL: <a href=\"https://news.ycombinator.com/item?id=23651117\">https://news.ycombinator.com/item?id=23651117</a></p>\n<p>Points: 1</p>\n<p># Comments: 0</p>\n"
                          :comments "https://news.ycombinator.com/item?id=23651117"
                          :dc:creator "seacaster"
                          :pubDate "Fri, 26 Jun 2020 12:17:33 +0000"
                          :contentSnippet
                          "Article URL: https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/\nComments URL: https://news.ycombinator.com/item?id=23651117\nPoints: 1\n# Comments: 0"
                          :title "Taking Theatre Online with WebGL and WebRTC"
                          :link
                          "https://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"
                          :guid "https://news.ycombinator.com/item?id=23651117"})

(deftest items-should-be-parsed
  (is (= {:created-at #inst "2020-06-26T12:17:33.000-00:00"
          :text "Taking Theatre Online with WebGL and WebRTC\n\nhttps://chrisuehlinger.com/blog/2020/06/16/unshattering-the-audience-building-theatre-on-the-web-in-2020/"}
         (sut/parse-feed hnrss-org-feed-item)))
  (is (=  {:created-at #inst "2020-06-26T00:36:48.000-00:00", 
           :text "Datahike release 0.3.1\n\nhttps://www.reddit.com/r/Clojure/comments/hfxotu/datahike_release_031/"}
         (sut/parse-feed reddit-feed-item))))
