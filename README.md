# oakpal-exercise-solutions

These are solutions to the Exercise 4 requirements in [the `oakpal-lab-adaptto-2020` github repo](https://github.com/adamcin/oakpal-lab-adaptto-2020).

This repository is built on commit to [https://hub.docker.com/repository/docker/adamcin/oakpal-exercise-solutions].

You can run the OPEAR using the command:

```bash
docker run -it --rm -v $(pwd):/work \
  adamcin/oakpal-exercise-solutions:latest \
  classic-app/all/target/classic-app.all-1.0-SNAPSHOT.zip
```
