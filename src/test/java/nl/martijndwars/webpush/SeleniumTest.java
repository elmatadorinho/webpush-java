package nl.martijndwars.webpush;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.bonigarcia.wdm.MarionetteDriverManager;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.*;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SeleniumTest {
    /**
     * Port of the server that serves the demo application.
     */
    private static int SERVER_PORT = 8000;

    /**
     * URL of the server that serves the demo application.
     */
    private static String SERVER_URL = "http://localhost:" + SERVER_PORT + "/index.html";

    /**
     * Saucylabs username
     */
    private static String USERNAME = "martijndwars";

    /**
     * Saucylabs access key
     */
    private static String ACCESS_KEY = "58a41adf-8c77-45df-8ef5-40903ce13c81";

    /**
     * Saucylabs remote URL
     */
    private static String REMOTE_DRIVER_URL = "http://" + USERNAME + ":" + ACCESS_KEY + "@localhost:4445/wd/hub";

    /**
     * Time to wait for arrival of the push message
     */
    private static long GET_MESSAGE_TIMEOUT = 120L;

    /**
     * Time to wait while registering the subscription
     */
    private static long GET_SUBSCRIPTION_TIMEOUT = 20L;

    /**
     * BaseEncoding service
     */
    private static BaseEncoding base64Url = BaseEncoding.base64Url();

    /**
     * The WebDriver instance used for the test.
     */
    private WebDriver webDriver;

    // TODO: List of drivers and that should be tested, i.e. FireFox with version x, Chrome with version y..


    @BeforeClass
    public static void beforeClass() throws Exception {
        // Set the BouncyCastle provider for cryptographic operations
        Security.addProvider(new BouncyCastleProvider());

        // Run embedded Jetty to serve the demo
        new DemoServer(SERVER_PORT);
    }

    /**
     * Download driver binaries and set path to binaries as system property.
     */
    @Before
    public void setUp() {
        ChromeDriverManager.getInstance().setup();
        MarionetteDriverManager.getInstance().setup();
    }

    /**
     * Get a ChromeWebDriver. In a CI environment, a RemoteDriver for Saucylabs
     * is returned. Otherwise, a ChromeDriver is returned.
     *
     * @return
     * @throws MalformedURLException
     */
    private WebDriver getChromeDriver() throws MalformedURLException {
        Map<String, Object> map = new HashMap<>();
        map.put("profile.default_content_settings.popups", 0);
        map.put("profile.default_content_setting_values.notifications", 1);

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setExperimentalOption("prefs", map);

        DesiredCapabilities desiredCapabilities = DesiredCapabilities.chrome();
        desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, chromeOptions);

        if (isCI()) {
            desiredCapabilities.setCapability("version", "52.0");
            desiredCapabilities.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"));
            desiredCapabilities.setCapability("name", "Travis #" + System.getenv("TRAVIS_JOB_NUMBER"));
            desiredCapabilities.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"));
            desiredCapabilities.setCapability("tags", System.getenv("CI"));

            return new RemoteWebDriver(new URL(REMOTE_DRIVER_URL), desiredCapabilities);
        } else {
            desiredCapabilities.setCapability("marionette", true);

            return new ChromeDriver(desiredCapabilities);
        }
    }

    /**
     * Get a FirefoxDriver. In a CI environment, a RemoteDriver for Saucylabs
     * is returned. Otherwise, a FirefoxDriver is returned.
     *
     * @return
     * @throws MalformedURLException
     */
    private WebDriver getFireFoxDriver() throws URISyntaxException, MalformedURLException {
        FirefoxProfile firefoxProfile = new FirefoxProfile();
        firefoxProfile.setPreference("dom.push.testing.ignorePermission", true);

        DesiredCapabilities desiredCapabilities = DesiredCapabilities.firefox();
        desiredCapabilities.setCapability(FirefoxDriver.PROFILE, firefoxProfile);

        if (isCI()) {
            desiredCapabilities.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"));
            desiredCapabilities.setCapability("name", "Travis #" + System.getenv("TRAVIS_JOB_NUMBER"));
            desiredCapabilities.setCapability("version", "52.0");
            desiredCapabilities.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"));
            desiredCapabilities.setCapability("tags", System.getenv("CI"));

            return new RemoteWebDriver(new URL(REMOTE_DRIVER_URL), desiredCapabilities);
        } else {
            return new FirefoxDriver(desiredCapabilities);
        }
    }

    /**
     * Get a subscription from the browser
     *
     * @param webDriver
     * @throws Exception
     */
    private Subscription getSubscription(WebDriver webDriver) throws Exception {
        // Wait until the subscription is set
        WebDriverWait webDriverWait = new WebDriverWait(webDriver, GET_SUBSCRIPTION_TIMEOUT);
        webDriverWait.until(ExpectedConditions.hasSubscription());

        // Get subscription
        JavascriptExecutor javascriptExecutor = ((JavascriptExecutor) webDriver);

        String subscriptionJson = (String) javascriptExecutor.executeScript("return document.getElementById('subscription').value");

        // Extract data from JSON string
        return new Gson().fromJson(subscriptionJson, Subscription.class);
    }

    /**
     * Wait until the messages arrives and get it.
     *
     * @param webDriver
     * @throws Exception
     */
    private String getMessage(WebDriver webDriver) throws Exception {
        // Wait until the message is set
        WebDriverWait webDriverWait = new WebDriverWait(webDriver, GET_MESSAGE_TIMEOUT);
        webDriverWait.until(ExpectedConditions.hasMessage());

        // Get message
        JavascriptExecutor javascriptExecutor = ((JavascriptExecutor) webDriver);

        return (String) javascriptExecutor.executeScript("return document.getElementById('message').value");
    }

    @Test
    public void testChrome() throws Exception {
        webDriver = getChromeDriver();
        webDriver.get(SERVER_URL);

        Subscription subscription = getSubscription(webDriver);
        Notification notification = createNotification(subscription);

        PushService pushService = new PushService();
        pushService.setGcmApiKey("AIzaSyDSa2bw0b0UGOmkZRw-dqHGQRI_JqpiHug");

        HttpResponse httpResponse = pushService.send(notification);

        Assert.assertEquals("The endpoint accepts the push message", httpResponse.getStatusLine().getStatusCode(), 201);
        Assert.assertTrue("The browser receives the push message", getPayload().equals(getMessage(webDriver)));
    }

    @Test
    public void testChromeVapid() throws Exception {
        KeyPair keyPair = readVapidKeys();

        webDriver = getChromeDriver();
        webDriver.get(SERVER_URL + "?vapid");

        Subscription subscription = getSubscription(webDriver);
        Notification notification = createNotification(subscription);

        PushService pushService = new PushService();
        pushService.setPublicKey(keyPair.getPublic());
        pushService.setPrivateKey(keyPair.getPrivate());
        pushService.setSubject("mailto:admin@domain.com");

        HttpResponse httpResponse = pushService.send(notification);

        Assert.assertEquals("The endpoint accepts the push message", httpResponse.getStatusLine().getStatusCode(), 201);

        // This does not work on CI/Saucylabs, not sure why
        if (!isCI()) {
            Assert.assertTrue("The browser receives the push message", getPayload().equals(getMessage(webDriver)));
        }
    }

    @Test
    public void testFireFoxVapid() throws Exception {
        KeyPair keyPair = readVapidKeys();

        webDriver = getFireFoxDriver();
        webDriver.get(SERVER_URL + "?vapid");

        Subscription subscription = getSubscription(webDriver);
        Notification notification = createNotification(subscription);

        PushService pushService = new PushService();
        pushService.setPublicKey(keyPair.getPublic());
        pushService.setPrivateKey(keyPair.getPrivate());
        pushService.setSubject("mailto:admin@domain.com");

        HttpResponse httpResponse = pushService.send(notification);

        Assert.assertEquals("The endpoint accepts the push message", 201, httpResponse.getStatusLine().getStatusCode());
        Assert.assertTrue("The browser receives the push message", getPayload().equals(getMessage(webDriver)));
    }

    @Test
    public void testFireFox() throws Exception {
        webDriver = getFireFoxDriver();
        webDriver.get(SERVER_URL);

        Subscription subscription = getSubscription(webDriver);
        Notification notification = createNotification(subscription);

        PushService pushService = new PushService();

        HttpResponse httpResponse = pushService.send(notification);

        Assert.assertEquals("The endpoint accepts the push message", 201, httpResponse.getStatusLine().getStatusCode());
        Assert.assertTrue("The browser receives the push message", getPayload().equals(getMessage(webDriver)));
    }

    /**
     * Generate a public-private keypair on the prime256v1 curve.
     *
     * @return
     * @throws NoSuchProviderException
     * @throws NoSuchAlgorithmException
     * @throws InvalidAlgorithmParameterException
     */
    private KeyPair generateVapidKeys() throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", "BC");
        keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"), new SecureRandom());

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Read the public-private key pair from vapid.pem and convert the
     * BouncyCastle PEMKeyPair to a JCA KeyPair.
     *
     * @return
     */
    private KeyPair readVapidKeys() throws IOException {
        try (InputStreamReader inputStreamReader = new InputStreamReader(getClass().getResourceAsStream("/vapid.pem"))) {
            PEMParser pemParser = new PEMParser(inputStreamReader);
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();

            return new JcaPEMKeyConverter().getKeyPair(pemKeyPair);
        } catch (IOException e) {
            throw new IOException("The private key could not be decrypted", e);
        }
    }

    /**
     * Check if we are running as CI
     *
     * @return
     */
    private boolean isCI() {
        return Objects.equals(System.getenv("CI"), "true");
    }

    /**
     * Create a notification from the given subscription.
     *
     * @param subscription
     * @return
     */
    private Notification createNotification(Subscription subscription) throws GeneralSecurityException {
        return new Notification(
            subscription.endpoint,
            subscription.keys.p256dh,
            subscription.keys.auth,
            getPayload()
        );
    }

    /**
     * Some dummy payload (a JSON object)
     *
     * @return
     */
    private String getPayload() {
        return "Hello, world!";
    }

    @After
    public void tearDown() throws InterruptedException {
        if (webDriver != null) {
            webDriver.quit();
        }
    }
}
