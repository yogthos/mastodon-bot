### description

the bot will post the timeline from the specified Twitter/Tumblr accounts and RSS feeds to Mastodon

### installation

1. install [Node.js](https://nodejs.org/en/)
2. run `npm install` to install Node modules

If you wish to run the script directly, you will need to have [Lumo](https://github.com/anmonteiro/lumo) available on the shell path. Lumo can be installed globally via NPM by running:

    npm install -g lumo-cljs

If you get a [permission failure](https://github.com/anmonteiro/lumo/issues/206), try this:

    npm install -g lumo-cljs --unsafe-perm


### usage

* create a Mastodon API key following the instructions [here](https://tinysubversions.com/notes/mastodon-bot/)
* create a Twitter API key following the instructions [here](https://developer.twitter.com/en/docs/basics/authentication/guides/access-tokens)
* create a Tumblr API key following the instructions [here](http://www.developerdrive.com/2014/05/how-to-get-started-with-the-tumblr-api-part-1/)
* create a file called `config.edn` with the following contents:

**NOTE**: the bot checks the specified Mastodon account to see the timestamp of the last post, and only posts content
with later timestamps to avoid duplicate posts. On the first run the timestamp will default to current time.

```clojure
{;; add Twitter config to mirror Twitter accounts
 :twitter {:access-keys
           {:consumer_key "XXXX"
            :consumer_secret "XXXX"
            :access_token_key "XXXX"
            :access_token_secret "XXXX"}}
 ;; add Tumblr config to mirror Tumblr accounts
 :tumblr {:access-keys
          {:consumer_key "XXXX"
           :consumer_secret "XXXX"
           :token "XXXX"
           :token_secret "XXXX"}
          ;; optional limit for number of posts to retrieve, default: 5
          :limit 10
          :accounts ["cyberpunky.tumblr.com" "scipunk.tumblr.com"]}
 ;; add RSS config to follow feeds
 :rss {"Hacker News" "https://hnrss.org/newest"
       "r/Clojure" "https://www.reddit.com/r/clojure/.rss"}
 :mastodon {:access_token "XXXX"
            ;; account number you see when you log in and go to your profile
            ;; e.g: https://mastodon.social/web/accounts/294795
            :account-id "XXXX"
            :api_url "https://botsin.space/api/v1/"
            ;; optional boolean to mark content as sensitive
            :sensitive? true
            ;; optional boolean defaults to false
            ;; only sources containing media will be posted when set to true
            :media-only? true
            ;; optional limit for the post length
            :max-post-length 300
            ;; optionally try to resolve URLs in posts to skip URL shorteners
            ;; defaults to false
            :resolve-urls? true
            ;; optional content filter regexes
            ;; any posts matching the regexes will be filtered out
            :content-filters [".*bannedsite.*"]
            ;; optional keyword filter regexes
            ;; any posts not matching the regexes will be filtered out
            :keyword-filters [".*clojure.*"]
            ;; Replace Twitter links by Nitter
            :nitter-urls? false}
:transform [{:source {:type :twitter-source
                       ;; optional, defaults to false
                       :include-replies? false
                       ;; optional, defaults to false
                       :include-rts? false
                       ;; accounts you wish to mirror
                       :accounts ["arstechnica" "WIRED"]}
             :target {:type :mastodon-target
                      ;; optional flag specifying wether the name of the account
                      ;; will be appended in the post, defaults to false
                      :append-screen-name? false
                      ;; optional visibility flag: direct, private, unlisted, public
                      ;; defaults to public
                      :visibility "unlisted"
                      ;; optional signature for posts
                      :signature "#newsbot"}}]
}
```

* the bot looks for `config.edn` at its relative path by default, an alternative location can be specified either using the `MASTODON_BOT_CONFIG` environment variable or passing the path to config as an argument

* run the bot: `npm start`
* to poll at intervals setup a cron job such as:

    */30 * * * * npm start /path/to/config.edn > /dev/null 2>&1

### compiling to Js

Alternatively, the bot can be compiled directly to JavaScript using the following command:

```
npx shadow-cljs release app
```

## License

Copyright © 2018 Dmitri Sotnikov

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
