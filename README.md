### description

the bot will read the timeline from the specified Twitter accounts,
and post it to Mastodon

### installation

1. install [Node.js](https://nodejs.org/en/)
2. run `npm install` to install Node modules
3. run `npm start` to, well, start

### usage

* create a Mastodon API key following the instructions [here](https://tinysubversions.com/notes/mastodon-bot/)
* create a Twitter API key follwing the instructions [here](https://developer.twitter.com/en/docs/basics/authentication/guides/access-tokens)
* create a file called `config.edn` with the following contents:

```clojure
{:twitter {:access-keys
           {:consumer_key "XXXX"
            :consumer_secret "XXXX"
            :access_token_key "XXXX"
            :access_token_secret "XXXX"}
           :accounts ["arstechnica" "WIRED"]} ;; accounts you wish to mirror
 :mastodon {:access_token "XXXX"
            :api_url "https://botsin.space/api/v1/"}}
```
* the bot looks for `config.edn` at its relative path by default, an alternative location can be specified either using the `MASTODON_BOT_CONFIG` environment variable or passing the path to config as an argument

* run the bot: `./mastodon-bot.cljs`
* to poll at intervals setup a cron job such as:

    */30 * * * * mastodon-bot.cljs /path/to/config.edn > /dev/null 2>&1
