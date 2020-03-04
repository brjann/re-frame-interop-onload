FROM clojure:lein-2.9.1

EXPOSE 3000

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

# Copy project file and run lein to install dependencies
COPY project.clj /usr/src/app/
RUN lein deps

# Copy the app
COPY . /usr/src/app

# Copy config
COPY local.example.edn local.edn

# Build uberjar
RUN lein uberjar
