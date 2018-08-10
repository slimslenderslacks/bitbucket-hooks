#!/usr/bin/env node

var cli = require("./index.js")
var argv = require('yargs')

  .usage('Usage: $0 <command> [options]')

  .command('checkProject', 'watch our git_incoming stream',
    (yargs) => {
      yargs
      .option('server', {
        describe: 'kafka group'})
      .option('project', {
        describe: 'staging or prod environment'})
      .option('url', {
        describe: 'webhook url to check'})
    },
    (argv) => {
       cli.checkProject({server: argv.server,
                         project: argv.project,
                         username: argv.username,
                         password: argv.password,
                         url: argv.url});
    })
  .command('onRepo', 'run this each time a new repo is added',
    (yargs) => {},
    (argv) => {
      cli.onRepo({}, "");
    })

  .argv
