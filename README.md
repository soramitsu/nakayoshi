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

Following command should be ran in the cloned repository folder.

```sh
$ sbt docker:publishLocal
```

After that you will find an image tagged `nakayoshi`

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
docker tag nakayoshi:latest  soramitsu/nakayoshi:0.1.1 
docker login --username=soramitsu
docker push soramitsu/nakayoshi:latest
docker push soramitsu/nakayoshi:0.1.1
```
