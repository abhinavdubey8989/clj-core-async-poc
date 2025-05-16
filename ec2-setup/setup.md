
# 1 Spawn ec2
- spawn ec2 of type : t2.2xLarge

# 2 ssh into EC2
ssh -i ~/Documents/ad/aws-and-docker/0_secrets/aws_ad89.pem ubuntu@13.201.32.143

# 3 Install Java 8
- sudo apt-get update
- sudo apt-get install openjdk-8-jdk

# 4 check java installed
- java -version
- which java (must give `/usr/bin/java`)

# 5 Set Java Home env variable
- edit this file using vim : `vim /etc/environment`
- add this line at last : `JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64"`
- load this env : `source /etc/environment`
- check if loaded correctly : `echo $JAVA_HOME`

# 6 Run a simple java program
- create a java file & write a `sysout  "hello world"`
- compile using : `javac <file_name>.java`
- run it using : `java <file_name>`
- it should print as expected

# 4 install lein -
- wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
- chmod +x lein
- sudo mv lein /usr/local/bin
- lein -v (Result : `Leiningen 2.11.2 on Java 1.8.0_452 OpenJDK 64-Bit Server VM`)


# 5 clone git repo

# 6 Start repl & connect to it
- start repl using : `lein repl :headless`
- in another terminal, connect to it using : `lein repl :connect localhost:45411`

