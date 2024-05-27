# api-scripts
## Instructions
1. Ensure you have Java 11 and up running on your machine
2. Create an `app.properties` file by coping over the contents of `sample-app.properties`
3. Fill out the values e.g CLIENT_ID=[some client id]. There is no need for quotation marks
4. Create a csv with user credentials e.g. `user_credentials.csv` and fill it with the user credentials. See format below

```csv
USERNAME,PASSWORD
username1,password1
username2,passsword2

```
**NOTE:** The first line is a header and will always be skipped by the script. Always include it as is.

### Generating the Scripts JAR

To generate the scripts api JAR file, execute the following command from the plugins
module:

```console
$ mvn clean package
```
This will generate a jar file in the following location `target/anc-script-jar-with-dependencies.jar`

### Running the the Scripts JAR

Then run it like this:
```console
$ java -jar /path/to/anc-script-jar-with-dependencies.jar -p=/path/to/app.properties -c=/path/to/user-credentials.csv
```

**NOTE** - When specifying each flag, _Do Not_ have any spaces between the flag and the filepath _i.e. -p=/path/to/file_

## Contribution
- Requires InjelliJ IDE. This can be [downloaded here](https://www.jetbrains.com/idea/download/)

### Code
- Before making a PR with your changes, always run File Formatter. On IDE go to **Code > Reformat File**

### To run the the Scripts JAR in Development Mode
- Import the project into the IDE
- Edit Run configurations for *App* and add *Program arguments* as 
`-p=/path/to/app.properties -c=/path/to/user-credentials.csv`
- Run the application