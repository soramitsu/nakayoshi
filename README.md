# Nakayoshi

This is the source code repository for [Nakayoshi], a tool for bridging communities across different chat services.
Currently we support three chat platforms:
 - Telegram supergroups
 - Gitter rooms
 - Chatgroups on any Rocketchat-based platform. Tested on version 0.62.0

[Nakayoshi]: https://github.com/soramitsu/nakayoshi

## Deployment

This project uses [sbt] as a build system.

[sbt]: https://www.scala-sbt.org/

### Building a Docker image

Following command should be ran in the cloned repository folder.

```sh
$ sbt docker:publishLocal
```

After that you will find an image tagged `chattohashi`

Example `docker-compose.yml`:

```yml
version: '3'

services:
  bot:
    image: chattohashi:0.1
    volumes:
      - ./files:/opt/docker/files:rw
      - ./settings:/opt/docker/settings:rw
```