# Releasing

## dev release
```
shadow-cljs compile test

shadow-cljs release app
chmod a+x mastodon-bot.js
rm -rf target/npm-build 
mkdir -p target/npm-build/mastodon_bot
cp mastodon-bot.js target/npm-build/mastodon_bot/
cp package.json target/npm-build/mastodon_bot/
cp README.md target/npm-build/mastodon_bot/
tar -cz -C target/npm-build -f target/npm-build.tgz .

npm publish ./target/npm-build.tgz --access public
```

## prod release (should be done from master)
```
shadow-cljs compile test

#adjust version
vi shadow-cljs.edn

git commit -am "releasing"
git tag [version]
git push && git push --tag

shadow-cljs release app

shadow-cljs release app
chmod a+x mastodon-bot.js
rm -rf target/npm-build 
mkdir -p target/npm-build/mastodon_bot
cp mastodon-bot.js target/npm-build/mastodon_bot/
cp package.json target/npm-build/mastodon_bot/
cp README.md target/npm-build/mastodon_bot/
tar -cz -C target/npm-build -f target/npm-build.tgz .

npm publish ./target/npm-build.tgz --access public

# Bump version
vi shadow-cljs.edn

git commit -am "version bump" && git push
```
