### description

the bot will post the timeline from the specified Twitter/Tumblr accounts and RSS feeds to Mastodon

### installation

1. install [Node.js](https://nodejs.org/en/)
2. run `npm install` to install Node modules
3. run `npm start` to, well, start

### usage

* create a Mastodon API key following the instructions [here](https://tinysubversions.com/notes/mastodon-bot/)
* create a Twitter API key follwing the instructions [here](https://developer.twitter.com/en/docs/basics/authentication/guides/access-tokens)
* create a Tumblr API key following the instructions [here](http://www.developerdrive.com/2014/05/how-to-get-started-with-the-tumblr-api-part-1/)
* create a file called `config.edn` with the following contents:

```clojure
{;; add Twitter config to mirror Twitter accounts
 :twitter {:access-keys
           {:consumer_key "XXXX"
            :consumer_secret "XXXX"
            :access_token_key "XXXX"
            :access_token_secret "XXXX"}
           :accounts ["arstechnica" "WIRED"]} ;; accounts you wish to mirror
 ;; add Tumblr config to mirror Tumblr accounts
 :tumblr {:access-keys
          {:consumer_key "XXXX"
           :consumer_secret "XXXX"
           :token "XXXX"
           :token_secret "XXXX"}
          :accounts ["cyberpunky.tumblr.com" "scipunk.tumblr.com"]}
 ;; add RSS config to follow feeds
 :rss {"Hacker News" "https://hnrss.org/newest"
       "r/Clojure" "https://www.reddit.com/r/clojure/.rss"}
 :mastodon {:access_token "XXXX"
            :api_url "https://botsin.space/api/v1/"
            :max-post-length 300}}
```

* the bot looks for `config.edn` at its relative path by default, an alternative location can be specified either using the `MASTODON_BOT_CONFIG` environment variable or passing the path to config as an argument

* run the bot: `./mastodon-bot.cljs`
* to poll at intervals setup a cron job such as:

    */30 * * * * mastodon-bot.cljs /path/to/config.edn > /dev/null 2>&1
