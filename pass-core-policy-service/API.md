# Web API

## Policies

The policy service has a `/policies` endpoint that determines the set of policies that are applicable to
a given submission.  Note:  The results may be dependent on _who_ submits the request.  For example, if
somebody from JHU invokes the policies endpoint, a general "policy for JHU employees" may be included in the results.

### Policies Request

`GET /policy-service/policies?submission=${SUBMISSION_ID}`

### Policies Response

The response is a list of IDs to Policy resources, decorated with a `type` property:

```json
[
 {
   "id": "3",
   "type": "funder"
 },
 {
   "id": "22",
   "type": "institution"
 }
]
```


## Repositories

The policy service has a `/repositories` endpoint that, for a given submission, calculates the repositories that may be
deposited into in order to satisfy any applicable policies for that submission.

### Repositories Request

GET `/policy-service/repositories?submission=${SUBMISSION_ID}`
or, with urlencoded (with encoded submission=${SUBMISSION_ID}) as the body:

```HTTP
POST /policy-service/repositories
Content-Type: application/x-www-form-urlencoded
```

### Repositories Response

Response an application/json document that lists repositories as follows:

```json
[
   {
     "url": "http://pass.local/fcrepo/rest/repositories/1",
     "selected": false
   },
   {
     "url": "http://pass.local/fcrepo/rest/repositories/2",
     "selected": false
   },
   {
     "url": "http://pass.local/fcrepo/rest/repositories/3",
     "selected": false
   },
   {
     "url": "http://pass.local/fcrepo/rest/repositories/4",
     "selected":false
   },
   {
     "url": "http://pass.local/fcrepo/rest/repositories/5",
     "selected": false
   },
   {
     "url": "http://pass.local/fcrepo/rest/repositories/6",
     "selected": true
   }
]

```


Repositories contained within each of the above lists are JSON objects containing the following fields:

* `url`: the URL to the repository resource in Fedora
* `selected`: optional field.  Specifies if the repository should be selected by default in the UI or not.

