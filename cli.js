#!/usr/bin/env node

var cli = require("./index.js")
var argv = require('yargs')

  .usage('Usage: $0 <command> [options]')

  .command(
    'converge <type>',
    'converge a webhook policy',
    (yargs) => {
      yargs
        .option('server', {describe: 'api root url'})
        .option('project', {describe: 'bitbucket project'})
        .option('url', {describe: 'atomist webhook url'})
        .option('username', {describe: 'admin username'})
        .option('password', {describe: 'admin password'})
    },
    (argv) => {
      if ("bitbucket" == argv.type) {
        cli.converge({server: argv.server,
                      project: argv.project,
                      username: argv.username,
                      password: argv.password,
                      url: argv.url});
      }
      else {
        console.log(`${argv.type} is not a valid resource type`);
      }
    }
  )

  .argv
