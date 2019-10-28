# Инициализация проекта
```bash
git clone git@gitlab.com:spectrum-internal/utils/connection-manager.git
cd connection-manager
git submodule add ../../buildSrc ./buildSrc
./buildSrc/init
./gradlew createmodule -Pmodulename=commons,bundle,postgres,rabbit,cassandra,elastic,elassandra,hdfs,s3
```