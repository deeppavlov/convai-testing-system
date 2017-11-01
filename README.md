convai-testing-system
=========================

This software is proposed to serve as host for some specific kind of Turing test.

People are connected to other people or bots randomly through this system. While having a conversation a person is supposed to evaluate utterances of his/her interlocutor. After finishing the conversation the person is need to evaluate overall dialog quality.

The system has following components:
* Telegram bot API connector - for people to connect to it through Telegram IM system (to use this feature you need [Telegram bot](https://core.telegram.org/bots) token);
* server-side API - for bots to connect to system;
* Facebook Messenger API connector (to use this feature you need to register your [Facebook bot] (https://developers.facebook.com/docs/messenger-platform)).

## Installation

### From Ubuntu package
You can download latest Ubuntu package [here](https://github.com/deepmipt/convai-testing-system/releases). 
To install this package execute: ```dpkg -i convai-testing-system.deb```.

### From source
To install from source please checkout this repository and run ```sbt run``` command inside project root folder.

## Configuration

The default config is placed at `/etc/convai-testing-system/reference.conf`. You could see the sample config [here](./src/main/resources/reference.conf).

The variables represented in such way `${?VARIABLE}` could be set as environment variable, e.g. by `export $VARIABLE` command in command line.

### Config blocks

#### Telegram

```
telegram {
  token = ${?TOKEN}
  webhook = ${?WEBHOOK}
}
```

Here you should place a token and a URL for webhook to connect to Telegram server. These values could be obtained [here](https://core.telegram.org/bots).

#### Facebook

```
fbmessenger {
  secret = ${?FB_SECRET}
  token = ${?FB_TOKEN}
  pageAccessToken = ${?FB_PAGE_ACCESS_TOKEN}
}
```

Here you should place a token, a secret and page access token for your [Facebook bot](https://developers.facebook.com/docs/messenger-platform).

#### Bot UUIDs

```
bot {
  registered = [
    { token: "5319E57A-F165-4BEC-94E6-413C38B4ACF9", max_connections: 1000, delayOn: true },
    { token: "0A36119D-E6C0-4022-962F-5B5BDF21FD97", max_connections: 1000, delayOn: true }
  ]
}
```

`token` here is UUID identifier for a bot. 

`max_connections` - max number of simultaneous connections. 

`delayOn` - is system need to add random delay for a bot responce. In our experiments some bots need this and some don't. So try for yoursef.

#### Conversation preperties

```
talk {
  talk_timeout = 10 minutes
  talk_length_max = 1000
  bot {
    human_bot_coefficient = 0.2
    delay {
      mean_k = 0.5
      variance = 5
    }
  }
 ```
  
`talk_timeout` - max duration of a conversation.

`talk_length_max` - max number of references.

`human_bot_coefficient` - percentage of humans which a picked for conversations. Example: `human_bot_coefficient = 0.2` like in the sample config, then for 10 people starting dialog 2 of them will have conversation with human, while others will be connected to bots.

`mean_k` & `variance` - parameters of normal distribution which is used to generate utterance delay. Used only if `delayOn` is set `true` for the bot.

#### Logging 

```
  logger {
    connection_string = ${?MONGODB_URI}
  }
```
  
`MONGODB_URI` - mongo DB URI used to store logs. The dialogs should be in collection `dialogs` inside MongoDB, the assessments will be inside `assessments`.

#### Context (or seed text)

```
  context {
    type = "wikinews"
  }
```

For now we have only one option - parsed SQuAD dataset, which is included in the bundle.



## Creating a package of your own

You could create a package by yourself if you want. You will need [sbt](http://www.scala-sbt.org/) first. After that, run in root folder of the project:
 `sbt debian:packageBin`


