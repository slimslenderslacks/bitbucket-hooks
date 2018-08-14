## Install

```
npm install
npm run compile
```

## converge a bitbucket

```
node cli.js converge bitbucket --server http://bitbucket-server.com:7990/ \
                               --project SLIM \
                               --username slimslenderslacks \
                               --password XXXXXXXXXXXXXXXXX \
                               --url https://webhook-staging.atomist.services/atomist/ingestion/endpoint
```