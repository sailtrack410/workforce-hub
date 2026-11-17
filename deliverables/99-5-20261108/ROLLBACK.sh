#!/bin/sh
set -eu
target=${1:?Usage: ROLLBACK.sh TARGET_COPY}
[ -d "$target" ] || exit 2
rm -f "$target/server/src/main/java/com/workforcehub/common/util/DatabaseIdGenerator.java"
rm -f "$target/server/src/main/java/db/migration/V70__create_durable_id_sequence.java"
rm -f "$target/server/src/test/java/com/workforcehub/common/util/IdGeneratorTest.java"
rm -f "$target/server/src/test/java/com/workforcehub/common/util/IdGeneratorMysqlTest.java"
rm -f "$target/server/src/test/java/com/workforcehub/common/util/IdGeneratorProcessProbe.java"
rm -f "$target/server/src/test/java/com/workforcehub/common/util/StandaloneIdGeneratorExtension.java"
rm -f "$target/server/src/test/resources/junit-platform.properties"
rm -f "$target/server/src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension"
base64 -d <<'PRISTINE_BASE64' | tar -xzf - -C "$target"
H4sIAHp28GoC/+2Vz2sTQRTHc96/Yo4JLbM/miYtacFAQQMtCOldpptJM3Z/hNnZJlIC3ppKS0XUgoqChxYiGETREut/003aU/8FZ7PbbVIterGH+D6X3cy8931v3pu3wSpW79wnzXuUVChP/RO0iJuemjaTvXoP13XN0I0UaqZuAd8ThMvwqf8TYw7Zgtl0Uc/PzeRz8zlDx3puZl6fzyspYOLxKN+kXPW4qdqEOepDsklU07XVhss3qi43ac1fCxds11F9wSy1VLlLHcqJcDkOrf9u/nPZaMbzudlo1o1k5vWspo/PvzGbzeZSSLvN+fcIswQn5sZNhRKkWp28/tflick6RbLDeLTlOGo5DlteUBRm110uUNjv4ZLcdkyfc+oILG+CzUxcHD6WXWdd2qsqGnx8HOy+HOy0z193zp58QqWli5NX/YOv5wdf+u3PwbfDwdF3ZGjo9McemkJn253g/dugtx+0Dy5OdoO9F/03vX53v9/9MDh6Fux3B8/f9dtPB71DpGvS6Xj79LgjNZW6v2YxE1WZQyxkWsTz0MgNRVuKgiR1zjaJoEh2USTWVxmj5WJ59UFpCS0ihzZGNtLlR56gNo7Puiq/lCvMspiXzqCFBZl+pjAeYCS2NNka7rVikyjTOAUrDOvQpihVEsOQaN1tyFT+FLuQODVqzKIoLbhPR7USvVhCasYHxetUpDOFXy3DjKTZChE1bJNmWmYynbhPIf2aD6ui9KWkvDF1wmnRqZSleOwzPVTMXM8qhFPhc2e4Py7aUsbfflu/suAsqWD0Y6yOsXrYRCzc2OCy3vEpWkoL/uQAAAAAAAAAAAAAAAAAAAAAAAAAAACASeAnyXuuTwAoAAA=
PRISTINE_BASE64
