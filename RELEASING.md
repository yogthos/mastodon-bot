# Releasing

## dev release
```
# run the tests
shadow-cljs compile test

# release the app
shadow-cljs release app
chmod a+x mastodon-bot.js

# publish to npm
mkdir -p target/npm-build/mastodon_bot
cp mastodon-bot.js target/npm-build/mastodon_bot/
cp target/mastodon-bot.js.sha256 target/npm-build/mastodon_bot/
cp target/mastodon-bot.js.sha512 target/npm-build/mastodon_bot/
cp package.json target/npm-build/mastodon_bot/
cp README.md target/npm-build/mastodon_bot/
npm publish ./target/npm-build --access public
```

## stable release (should be done from master)
```
shadow-cljs compile test

#adjust [version]
vi package.json

git commit -am "releasing"
git commit -am [version]
git push --follow-tags

# Bump [version]
vi package.json

git commit -am "version bump" && git push


# trigger deploy
git tag [version]
git push --tags
```
