# PASS policy service

Contains the PASS policy service, which provides an HTTP API for determining the policies applicable to a given Submission, as well as the repositories that must be deposited into in order to comply with the applicable policies.

See the [Documentation for the API](API.md)

## Configuration
Configuration is achieved via the following environment variables:

* `INSTITUTION`: This is the institution as it is appears on User.affiliations for every user in the institution: e.g. "johnshopkins.edu"
* `INSTITUTIONAL_POLICY_TITLE`: The value of Policy.title on the institution's IR object
* `INSTITUTIONAL_REPOSITORY_NAME`: The value of Policy.title on the intitution's policy object