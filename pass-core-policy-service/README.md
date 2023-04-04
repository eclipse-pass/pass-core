# PASS policy service

Contains the PASS policy service, which provides an HTTP API for determining the policies applicable to a given Submission, as well as the repositories that must be deposited into in order to comply with the applicable policies.

See the [Documentation for the API](API.md)

## Configuration

Configuration is provided via a policy rules DSL file.  This is a JSON document that contains rules which govern which policies apply to a given
submission.  Documentation can be found in [the rule DSL docs](RULE.md)

An example of such configuration file can be found in the [test data](gsrc/test/resources/schemas/ood.json)


### Docker Configuration

The `POLICY_FILE` environment variable.  This points to a policy rules DSL file (accessible in the container, either built-in, or mounted)

Built-in policy files include `docker.json` (default, works in the `pass-docker` environment), and `aws.json` (works in an AWS environment).

Additional configuration is achieved via the following environment variables:

* `PASS_EXTERNAL_FEDORA_BASEURL`: External (public) Fedora PASS baseurl
* `PASS_FEDORA_BASEURL`: Internal (private) Fedora PASS baseurl
* `PASS_FEDORA_USER`: Username for basic auth to Fedora
* `PASS_FEDORA_PASSWORD`: Password for basic auth to Fedora
* `POLICY_SERVICE_PORT`: Port for policy service port (default is 0 for random)
