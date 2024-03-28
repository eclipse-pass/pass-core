<?php
// These attributes mimic those of Azure AD.
$test_user_base = array(
    'urn:oid:2.16.840.1.113730.3.1.241' => 'display name',
    'urn:oid:1.3.6.1.4.1.5923.1.1.1.9' => 'scoped affiliation',
    'urn:oid:0.9.2342.19200300.100.1.3' => 'email',
    'urn:oid:1.3.6.1.4.1.5923.1.1.1.6' => 'eppn',
    'urn:oid:2.5.4.42' => 'given name',
    'urn:oid:2.5.4.4' => 'surname',
    'urn:oid:2.16.840.1.113730.3.1.3' => 'employee id',
    'urn:oid:1.3.6.1.4.1.5923.1.1.1.13' => 'unique id'
);

$config = array(
    'admin' => array(
        'core:AdminPassword',
    ),
    'example-userpass' => array(
        'exampleauth:UserPass',
        'user1:password' => array_merge($test_user_base, array(
            'urn:oid:2.16.840.1.113730.3.1.241' => 'Sally M. Submitter',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.9' => 'FACULTY@johnshopkins.edu',
            'urn:oid:0.9.2342.19200300.100.1.3' => 'sally123456789@jhu.edu',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.6' => 'sallysubmitter123456789@johnshopkins.edu',
            'urn:oid:2.5.4.42' => 'Sally',
            'urn:oid:2.5.4.4' => 'Submitter',
            'urn:oid:2.16.840.1.113730.3.1.3' => '123456789',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.13' => 'sms123456789@johnshopkins.edu'
        )),
        'user2:password' => array_merge($test_user_base, array(
            'urn:oid:2.16.840.1.113730.3.1.241' => 'Thomas L. Submitter',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.9' => 'FACULTY@johnshopkins.edu',
            'urn:oid:0.9.2342.19200300.100.1.3' => 'tom987654321@jhu.edu',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.6' => 'thomassubmitter987654321@johnshopkins.edu',
            'urn:oid:2.5.4.42' => 'Tom',
            'urn:oid:2.5.4.4' => 'Submitter',
            'urn:oid:2.16.840.1.113730.3.1.3' => '987654321',
            'urn:oid:1.3.6.1.4.1.5923.1.1.1.13' => 'tls987654321@johnshopkins.edu'
        )),
    ),
);

