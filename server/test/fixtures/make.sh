#!/usr/bin/env bash
# Makes the fake store credentials the Pro tests use: a three-level certificate chain shaped like
# Apple's (with Apple's marker extensions, but our own root) and an RSA key for a fake Google service
# account. Test-only keys; nothing here is trusted outside test/quota.test.mjs.
#   bash test/fixtures/make.sh
set -e
cd "$(dirname "$0")"
cat > ext.cnf <<'EOF'
[req]
distinguished_name = dn
[dn]
[root]
basicConstraints = critical, CA:true
keyUsage = critical, keyCertSign, cRLSign
[intermediate]
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, keyCertSign, cRLSign
1.2.840.113635.100.6.2.1 = ASN1:NULL
[leaf]
basicConstraints = critical, CA:false
keyUsage = critical, digitalSignature
1.2.840.113635.100.6.11.1 = ASN1:NULL
EOF
openssl ecparam -name secp384r1 -genkey -noout -out root.key
openssl req -new -x509 -key root.key -sha384 -days 3650 -subj "/CN=Test Root G3" -extensions root -config ext.cnf -out root.pem
openssl ecparam -name prime256v1 -genkey -noout -out intermediate.key
openssl req -new -key intermediate.key -subj "/CN=Test WWDR" -config ext.cnf -out intermediate.csr
openssl x509 -req -in intermediate.csr -CA root.pem -CAkey root.key -CAcreateserial -sha384 -days 3650 -extfile ext.cnf -extensions intermediate -out intermediate.pem
openssl ecparam -name prime256v1 -genkey -noout -out leaf.key
openssl req -new -key leaf.key -subj "/CN=Test StoreKit" -config ext.cnf -out leaf.csr
openssl x509 -req -in leaf.csr -CA intermediate.pem -CAkey intermediate.key -CAcreateserial -sha256 -days 3650 -extfile ext.cnf -extensions leaf -out leaf.pem
openssl pkcs8 -topk8 -nocrypt -in leaf.key -out leaf.pk8
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out google.pk8
for c in root intermediate leaf; do openssl x509 -in $c.pem -outform der | base64 -w0 > $c.b64; done
rm -f *.csr *.srl ext.cnf root.key intermediate.key leaf.key
ls
