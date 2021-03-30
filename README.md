# なかよし

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
### Build and push a Docker image
```shell
docker run -it  \
   -v /var/run/docker.sock:/var/run/docker.sock \
   -v $PWD:/app \
   -w /app \
   hseeberger/scala-sbt:11.0.9.1_1.4.5_2.12.12 \
   bash -c 'apt install -y docker.io && sbt docker:publishLocal'

docker tag nakayoshi:latest  soramitsu/nakayoshi:latest 
docker tag nakayoshi:latest  soramitsu/nakayoshi:0.1.3 
docker login --username=soramitsu
docker push soramitsu/nakayoshi:latest
docker push soramitsu/nakayoshi:0.1.3
```
