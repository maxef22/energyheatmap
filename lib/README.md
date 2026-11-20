# Зависимости сборки

Эти файлы нужны для компиляции, но **в репозиторий не входят** (см. `.gitignore`, правило `*.jar`).

Положить в каталог на уровень выше папки проекта:

```
<parent>/
  directaccess-api-addon-1.7.0.jar
  json-20251224.jar
  Energyheatmap/
```

- `directaccess-api-addon-1.7.0.jar` — SDK аддонов WebCTRL (Automated Logic).
  Права принадлежат Automated Logic, публично не распространяется: брать из поставки WebCTRL / SDK.
- `json-20251224.jar` — библиотека [org.json](https://mvnrepository.com/artifact/org.json/json).

Если JAR лежат в другом месте, поправить пути в `nbproject/project.properties`:

```properties
file.reference.directaccess-api-addon-1.7.0.jar=../directaccess-api-addon-1.7.0.jar
file.reference.json-20251224.jar=../json-20251224.jar
javac.classpath=\
    ${file.reference.json-20251224.jar}:\
    ${file.reference.directaccess-api-addon-1.7.0.jar}
```
