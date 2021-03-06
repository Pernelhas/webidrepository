/*

Copyright (c) 2008-2010, The University of Manchester, United Kingdom.
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
 * Neither the name of The University of Manchester nor the names of 
      its contributors may be used to endorse or promote products derived 
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

  Author........: Bruno Harbulot
 
 */
package uk.ac.manchester.rcs.bruno.keygenapp.base;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * This class initialises the configuration of the "Mini CA" from servlet
 * parameters.
 * 
 * @author Bruno Harbulot.
 * 
 */
public class MiniCaConfiguration {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static Logger LOG = Logger.getLogger(MiniCaConfiguration.class
            .getCanonicalName());

    public final static String CERTIFICATE_JNDI_NAME = "webiddirectory/signingCertificate";
    public final static String PRIVATEKEY_JNDI_NAME = "webiddirectory/signingPrivateKey";

    public final static String KEYSTORE_JNDI_NAME = "webiddirectory/signingKeyStore";

    public final static String KEYSTORE_RESOURCE_PATH_JNDI_NAME = "webiddirectory/signingKeystoreResourcePath";
    public final static String KEYSTORE_PATH_JNDI_NAME = "webiddirectory/signingKeystorePath";
    public final static String KEYSTORE_TYPE_JNDI_NAME = "webiddirectory/signingKeystoreType";
    public final static String KEYSTORE_PASSWORD_JNDI_NAME = "webiddirectory/signingKeystorePassword";
    public final static String KEY_PASSWORD_JNDI_NAME = "webiddirectory/signingKeyPassword";
    public final static String ALIAS_JNDI_NAME = "webiddirectory/signingKeyAlias";

    public static final String ISSUER_NAME_JNDI_NAME = "webiddirectory/issuerName";

    private PrivateKey caPrivKey;
    private X509Certificate caCertificate;

    private X509Name issuerName;

    private Random random = new Random();

    public PrivateKey getCaPrivKey() {
        return this.caPrivKey;
    }

    public void setCaPrivKey(PrivateKey caPrivKey) {
        this.caPrivKey = caPrivKey;
    }

    public PublicKey getCaPublicKey() {
        return getCaCertificate().getPublicKey();
    }

    public X509Certificate getCaCertificate() {
        return this.caCertificate;
    }

    public void setCaCertificate(X509Certificate caCertificate) {
        this.caCertificate = caCertificate;
    }

    public X509Name getIssuerName() {
        return this.issuerName;
    }

    public void setIssuerName(X509Name issuerName) {
        this.issuerName = issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = new X509Name(issuerName);
    }

    public BigInteger nextCertificateSerialNumber() {
        byte[] randomBytes = new byte[24];
        random.nextBytes(randomBytes);
        randomBytes[0] = 1;
        return new BigInteger(randomBytes);
    }

    private Object loadJndiValue(Context ctx, String jndiName)
            throws NamingException {
        try {
            return ctx.lookup(jndiName);
        } catch (NameNotFoundException e) {
            LOG.log(Level.FINE, String.format("JNDI name not found (%s).",
                    jndiName), e);
        }
        return null;
    }

    /**
     * Initialises the servlet: loads the keystore/keys to use to sign the
     * assertions and the issuer name.
     */
    public void init() throws ConfigurationException {
        KeyStore keyStore = null;

        String keystoreResourcePath = null;
        String keystorePath = null;
        String keystoreType = null;
        char[] keystorePasswordArray = null;
        char[] keyPasswordArray = null;
        String alias = null;
        String issuerName = null;

        X509Certificate certificate = null;
        PrivateKey privateKey = null;

        try {
            Context initCtx = new InitialContext();
            Context ctx = (Context) initCtx.lookup("java:comp/env");
            try {
                keystoreResourcePath = (String) loadJndiValue(ctx,
                        KEYSTORE_RESOURCE_PATH_JNDI_NAME);

                keystorePath = (String) loadJndiValue(ctx,
                        KEYSTORE_PATH_JNDI_NAME);

                keystoreType = (String) loadJndiValue(ctx,
                        KEYSTORE_TYPE_JNDI_NAME);

                String keystorePassword = (String) loadJndiValue(ctx,
                        KEYSTORE_PASSWORD_JNDI_NAME);
                if (keystorePassword != null) {
                    keystorePasswordArray = keystorePassword.toCharArray();
                }
                String keyPassword = (String) loadJndiValue(ctx,
                        KEY_PASSWORD_JNDI_NAME);
                if (keyPassword != null) {
                    keyPasswordArray = keyPassword.toCharArray();
                } else {
                    keyPasswordArray = keystorePasswordArray;
                }

                alias = (String) loadJndiValue(ctx, ALIAS_JNDI_NAME);
                issuerName = (String) loadJndiValue(ctx, ISSUER_NAME_JNDI_NAME);

                keystoreType = (String) loadJndiValue(ctx,
                        KEYSTORE_TYPE_JNDI_NAME);

                certificate = (X509Certificate) loadJndiValue(ctx,
                        CERTIFICATE_JNDI_NAME);

                privateKey = (PrivateKey) loadJndiValue(ctx,
                        PRIVATEKEY_JNDI_NAME);

                keyStore = (KeyStore) loadJndiValue(ctx, KEYSTORE_JNDI_NAME);
            } finally {
                try {
                    try {
                        if (ctx != null) {
                            ctx.close();
                        }
                    } finally {
                        if (initCtx != null) {
                            initCtx.close();
                        }
                    }
                } catch (NamingException e) {
                    LOG.log(Level.SEVERE, "Unable to close JNDI context.", e);
                    throw new RuntimeException(e);
                }
            }
        } catch (NameNotFoundException e) {
            LOG.log(Level.INFO, "Unable to load JNDI context.", e);
        } catch (NamingException e) {
            LOG.log(Level.INFO, "Unable to load JNDI context.", e);
        }

        if (keyPasswordArray == null) {
            keyPasswordArray = keystorePasswordArray;
        }

        if ((certificate == null) || (privateKey == null)) {
            if (keyStore == null) {
                try {
                    InputStream ksInputStream = null;

                    try {
                        if (keystorePath != null) {
                            ksInputStream = new FileInputStream(keystorePath);
                        } else if (keystoreResourcePath != null) {
                            ksInputStream = MiniCaConfiguration.class
                                    .getResourceAsStream(keystoreResourcePath);
                        }
                        keyStore = KeyStore
                                .getInstance((keystoreType != null) ? keystoreType
                                        : KeyStore.getDefaultType());
                        keyStore.load(ksInputStream, keystorePasswordArray);
                    } finally {
                        if (ksInputStream != null) {
                            ksInputStream.close();
                        }
                    }
                } catch (FileNotFoundException e) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet (could not load keystore).",
                                    e);
                    throw new ConfigurationException("Could not load keystore.");
                } catch (KeyStoreException e) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet (could not load keystore).",
                                    e);
                    throw new ConfigurationException("Could not load keystore.");
                } catch (NoSuchAlgorithmException e) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet (could not load keystore).",
                                    e);
                    throw new ConfigurationException("Could not load keystore.");
                } catch (CertificateException e) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet (could not load keystore).",
                                    e);
                    throw new ConfigurationException("Could not load keystore.");
                } catch (IOException e) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet (could not load keystore).",
                                    e);
                    throw new ConfigurationException("Could not load keystore.");
                }
            }

            try {
                if (alias == null) {
                    Enumeration<String> aliases = keyStore.aliases();
                    while (aliases.hasMoreElements()) {
                        String tempAlias = aliases.nextElement();
                        if (keyStore.isKeyEntry(tempAlias)) {
                            alias = tempAlias;
                            break;
                        }
                    }
                    LOG.log(Level.INFO,
                            "Loading certificate from keystore with alias: "
                                    + alias);
                }
                if (alias == null) {
                    LOG
                            .log(
                                    Level.SEVERE,
                                    "Error configuring servlet, invalid keystore configuration: alias unspecified or couldn't find key at alias: "
                                            + alias);
                    throw new ConfigurationException(
                            "Invalid keystore configuration: alias unspecified or couldn't find key at alias: "
                                    + alias);
                }
                if (privateKey == null) {
                    privateKey = (PrivateKey) keyStore.getKey(alias,
                            keyPasswordArray);
                }
                if (certificate == null) {
                    certificate = (X509Certificate) keyStore
                            .getCertificate(alias);
                }
            } catch (UnrecoverableKeyException e) {
                LOG.log(Level.SEVERE,
                        "Error configuring servlet (could not load keystore).",
                        e);
                throw new ConfigurationException("Could not load keystore.");
            } catch (KeyStoreException e) {
                LOG.log(Level.SEVERE,
                        "Error configuring servlet (could not load keystore).",
                        e);
                throw new ConfigurationException("Could not load keystore.");
            } catch (NoSuchAlgorithmException e) {
                LOG.log(Level.SEVERE,
                        "Error configuring servlet (could not load keystore).",
                        e);
                throw new ConfigurationException("Could not load keystore.");
            }
        }

        if ((certificate == null) || (privateKey == null)) {
            LOG.log(Level.SEVERE, "Unable to load certificate or private key.");
            throw new ConfigurationException(
                    "Unable to load certificate or private key.");
        }

        setCaCertificate(certificate);
        setCaPrivKey(privateKey);
        setIssuerName(issuerName);
    }

    public static class ConfigurationException extends Exception {
        private static final long serialVersionUID = 4063300924799519997L;

        public ConfigurationException(String msg) {
            super(msg);
        }
    }
}
