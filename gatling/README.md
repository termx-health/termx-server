
## Test scenario 1
```
1. Search mapsets
2. Search association types
3. Search namin systems
4. Create new valueset
5. Load created valueset
6. Create version for valueset
7. Create new codesystem
8. Load created codesystem
9. Search codesystems
10. Search valuestes
```

## Configure
* gradle.properties
```
timeFactor = 1 ## test duration in minutes
userFactor = 700 ## number of scenarios to run, split evenly during `timeFactor` 
terminologyApi = https://kefhir.kodality.dev/fhir
accessToken = OAuth access token with admin privileges (for authenticated requests)

## Running load tests
`./gradlew clean gatlingRun`
