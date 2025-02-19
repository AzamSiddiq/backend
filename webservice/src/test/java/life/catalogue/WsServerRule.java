 package life.catalogue;

 import life.catalogue.common.io.PortUtils;
 import life.catalogue.db.PgConfig;
 import life.catalogue.db.PgSetupRule;
 import life.catalogue.dw.auth.BasicAuthClientFilter;

 import java.io.IOException;
 import java.util.List;
 import java.util.logging.Level;

 import org.apache.ibatis.session.SqlSessionFactory;
 import org.glassfish.jersey.client.JerseyClientBuilder;
 import org.glassfish.jersey.logging.LoggingFeature;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import org.testcontainers.containers.PostgreSQLContainer;

 import com.google.common.collect.Lists;

 import io.dropwizard.testing.ConfigOverride;
 import io.dropwizard.testing.junit.DropwizardAppRule;

 /**
 * An adaptation of the generic DropwizardAppRule that can be used as a junit class rule
 * to create integration tests against a running dropwizard instance.
 * <p>
 * WsServerRule connects to a postgres server, inits a new unique database
 * and updates the PgConfig with the matching config parameters to access it via the MyBatisModule.
 * <p>
 * It also selects and configures DW to use a free application port.
 *
 * The created jersey client includes basic auth which needs to be set for each call like this:
 *
 * <code>
 *      Response response = client.target("http://localhost:8080/rest/homer/contact").request()
 *         .property(HTTP_AUTHENTICATION_BASIC_USERNAME, "homer")
 *         .property(HTTP_AUTHENTICATION_BASIC_PASSWORD, "p1swd745").get();
 * </code>
 */
public class WsServerRule extends DropwizardAppRule<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(WsServerRule.class);

   private static final PostgreSQLContainer<?> CONTAINER = PgSetupRule.setupPostgres();

   public WsServerRule(String configPath, ConfigOverride... configOverrides) {
    super(WsServer.class, configPath, setupPg(configPath, configOverrides));
  }
  
  static class PgConfigInApp {
    public PgConfig db = new PgConfig();
  }

  public void startNamesIndex() throws Exception {
    getServer().getNamesIndex().start();
  }

  public void startImporter() throws Exception {
    getServer().getImportManager().start();
  }

  static ConfigOverride[] setupPg(String configPath, ConfigOverride... configOverrides) {
    List<ConfigOverride> overrides = Lists.newArrayList(configOverrides);
    try {
      LOG.info("Use Postgres container {}/{}", CONTAINER.getHost(), CONTAINER.getDatabaseName());
      CONTAINER.start(); // we need to start up the container to know the mapped port
      var cfg = PgSetupRule.buildContainerConfig(CONTAINER);
      PgSetupRule.initDb(CONTAINER, cfg);

      overrides.add(ConfigOverride.config("db.host", cfg.host));
      overrides.add(ConfigOverride.config("db.port", String.valueOf(cfg.port)));
      overrides.add(ConfigOverride.config("db.database", cfg.database));
      overrides.add(ConfigOverride.config("db.user", cfg.user));
      overrides.add(ConfigOverride.config("db.password", cfg.password));

    } catch (Exception e) {
      throw new RuntimeException("Failed to read postgres configuration from " + configPath, e);
    }

    // select free DW port
    try {
      int dwPort = PortUtils.findFreePort();
      int dwPortAdmin = PortUtils.findFreePort();
      LOG.info("Configure DW ports application={}, admin={}", dwPort, dwPortAdmin);
      overrides.add(ConfigOverride.config("server.applicationConnectors[0].port", String.valueOf(dwPort)));
      overrides.add(ConfigOverride.config("server.adminConnectors[0].port", String.valueOf(dwPortAdmin)));
    } catch (IOException e) {
      throw new RuntimeException("Failed to select free Dropwizard application port", e);
    }

    return overrides.toArray(new ConfigOverride[0]);
  }
  
  public SqlSessionFactory getSqlSessionFactory() {
    return ((WsServer) getTestSupport().getApplication()).getSqlSessionFactory();
  }

  public WsServer getServer() {
    return ((WsServer) getTestSupport().getApplication());
  }

  @Override
  protected JerseyClientBuilder clientBuilder() {
    JerseyClientBuilder builder = super.clientBuilder();
    BasicAuthClientFilter basicAuthFilter = new BasicAuthClientFilter();
    var logF = new LoggingFeature(java.util.logging.Logger.getLogger(getClass().getName()), Level.OFF, LoggingFeature.Verbosity.PAYLOAD_TEXT, 8192);
    builder.register(basicAuthFilter)
           .register(logF);
    return builder;
  }

   @Override
   protected void after() {
     super.after();
     CONTAINER.stop();
   }
 }
