## sscs-robotics-common

This is the common code library for sscs.

###Build

To build run

```bash
./gradlew clean build
```
This will create a jar file in the projects build libs directory.

To depend on this in other projects run

```bash
./gradlew install
```

This will install the jar into your local maven repo. Then just add a dependency the other project with a 
version of DEV-SNAPSHOT.

###Release candidate

To release a candidate

1. Commit and push upstream
2. Tag this commit and push the tag upstream
    ```bash
    git tag -a 0.0.85-CANDIDATE
    git push origin 0.0.85-CANDIDATE
    ```

This will start a pipeline that publishes a new artifact to bintray.
To use this new artifact, add the following to your build.gradle file in the dependencies section.

```gradle
compile group: 'uk.gov.hmcts.reform', name: 'sscs-pdf-robotics-email-common', version: '0.0.85-CANDIDATE'
```
