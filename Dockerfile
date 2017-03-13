FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/bass4.jar /bass4/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/bass4/app.jar"]
