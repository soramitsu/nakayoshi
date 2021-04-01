# なかよし
![version](https://img.shields.io/docker/v/soramitsu/nakayoshi?color=blue&sort=date)

This is the source code repository for [Nakayoshi], a tool for bridging communities across different chat services.
Currently we support three chat platforms:
 - Telegram supergroups
 - Gitter rooms
 - Chatgroups on any Rocketchat-based platform. Tested on version 3.12.3

[Nakayoshi]: https://github.com/soramitsu/nakayoshi

[Official Docker Image](https://hub.docker.com/r/soramitsu/nakayoshi)

## Deployment

This project uses [sbt] as a build system.

[sbt]: https://www.scala-sbt.org/

### Building a Docker image

**Requirements**
1. [sbt](https://www.scala-sbt.org/)
2. temporarily - [mill](https://com-lihaoyi.github.io/mill/)
3. temporarily - [git](https://git-scm.com/)

Following commands should be ran in the cloned repository folder.

```sh
$ sbt buildTelegramLibrarySnapshot
$ sbt docker:publishLocal
```

After that you will find an image tagged `nakayoshi`

#### Note on Telegram Library

Building telegram library this way is temporary until the new version is published. This command will checkout a fixed commit of the telegram library and build it locally. Make sure to have `mill` and `git` installed for the `sbt buildTelegramLibrarySnapshot` to be executed successfully.

#### Docker Compose Example

Example `docker-compose.yml`:

```yml
version: '3'

services:
  bot:
    image: nakayoshi:latest
    volumes:
      - ./files:/opt/docker/files:rw
      - ./settings:/opt/docker/settings:rw
```
### Setting Up The Bot

#### Configuration

The configuration file should be put in `./data/local.conf`

An example of configuration file contents:
```
telegram.token = "<tg-token>"
gitter.token = "<gitter-token>"
gitter.username = "<gitter-bot-username>"
rocketchat.path = "localhost"
rocketchat.user = "<rocketchat-bot-username>"
rocketchat.password = "<rocketchat-password>"
telegram.admin = ["<tg-id-without-@>"]

http.enabled = false
```

For more available fields and details see [Configuration.scala](./src/main/scala/jp/co/soramitsu/nakayoshi/Configuration.scala).

#### Rocketchat

The fastest way to set up Rocketchat on localhost is with docker-compose, see rocketchat [docs](https://docs.rocket.chat/installation/docker-containers/docker-compose).

Pay attention to also add this lines to `./data/local.conf` if you are not running a local SSL proxy:
```
rocketchat.ssl-enabled = false
rocketchat.port = 3000
```

Then before starting the Nakayoshi, follow this [guide](https://developer.rocket.chat/guides/bots/create-and-run-a-bot) to create a Rocketchat bot user.

#### Gitter

Obtain gitter token for a registered (bot) user at the [developer portal](https://developer.gitter.im/docs/welcome).

#### Telegram

Create a telegram bot with BotFather as specified in Telegram [docs](https://core.telegram.org/bots#6-botfather). Be sure to record the bot token.

### Running the bot

Once the bot is started, send it a direct message in Telegram with the command `/help`. It will print a list of bot command and how to use them to connect the chats in Telegram, Gitter and Rocketchat.

