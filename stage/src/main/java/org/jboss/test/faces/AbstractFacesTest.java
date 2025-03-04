/**
 * 
 */
package org.jboss.test.faces;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EventListener;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.LogManager;

import org.jboss.test.faces.staging.HttpConnection;
import org.jboss.test.faces.staging.StagingServer;
import org.junit.After;
import org.junit.Before;

import jakarta.faces.FacesException;
import jakarta.faces.FactoryFinder;
import jakarta.faces.application.Application;
import jakarta.faces.application.ApplicationFactory;
import jakarta.faces.application.ProjectStage;
import jakarta.faces.application.StateManager;
import jakarta.faces.application.ViewHandler;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.FacesContextFactory;
import jakarta.faces.lifecycle.Lifecycle;
import jakarta.faces.lifecycle.LifecycleFactory;
import jakarta.faces.render.RenderKitFactory;
import jakarta.faces.webapp.FacesServlet;
import jakarta.servlet.Filter;
import junit.framework.TestCase;

/**
 * Base class for all JSF test cases. 
 * @author asmirnov
 * 
 */
public abstract class AbstractFacesTest extends TestCase {

	private ClassLoader contextClassLoader;

	/**
	 * Prepared test server instance. Populated by the default {@link #setUp()} method.
	 */
	protected StagingServer facesServer;

	/**
	 * Current virtual connection. This field populated by the {@link #setupWebContent()} method only.
	 */
	protected HttpConnection connection;

	/**
	 * Current {@link FacesContext} instance. This field populated by the {@link #setupWebContent()} method only.
	 */
	protected FacesContext facesContext;

	/**
	 * JSF {@link Lifecycle} instance. Populated by the default {@link #setUp()} method.
	 */
	protected Lifecycle lifecycle;

	/**
	 * JSF {@link Application} instance. Populated by the default {@link #setUp()} method.
	 */
	protected Application application;

	/**
	 * Setup staging server instance with JSF implementation. First, this method creates a local test instance 
	 * and calls the other template method in the next sequence:
	 * <ol>
	 * <li>{@link #setupFacesServlet()}</li>
	 * <li>{@link #setupFacesListener()}</li>
	 * <li>{@link #setupJsfInitParameters()}</li>
	 * <li>{@link #setupWebContent()}</li>
	 * </ol>
	 * After them, test server is initialized as well as fields {@link #lifecycle} and {@link #application} populated.
	 * Also, if the resource "logging.properties" is exist in the test class package, The Java {@link LogManager} will be configured with its content.  
	 * @throws java.lang.Exception
	 */
	@Override
    @Before
	public void setUp() throws Exception {
		contextClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(
				this.getClass().getClassLoader());
		InputStream stream = this.getClass().getResourceAsStream(
				"logging.properties");
		if (null != stream) {
			try {
				LogManager.getLogManager().readConfiguration(stream);
			} catch (Exception e) {
				// Ignore it.
			} finally {
				try {
					stream.close();
				} catch (IOException e) {
					// Ignore it.
				}
			}
		}
		facesServer = new StagingServer();
		setupFacesServlet();
		setupFacesListener();
		setupJsfInitParameters();
		setupWebContent();
		facesServer.init();
		ApplicationFactory applicationFactory = (ApplicationFactory) FactoryFinder
				.getFactory(FactoryFinder.APPLICATION_FACTORY);
		application = applicationFactory.getApplication();
		LifecycleFactory lifecycleFactory = (LifecycleFactory) FactoryFinder
				.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
		lifecycle = lifecycleFactory
				.getLifecycle(LifecycleFactory.DEFAULT_LIFECYCLE);
	}

	/**
	 * This hook method called from the {@link #setUp()} should append JSF implementation
	 * listener to the test server. Default version applends "com.sun.faces.config.ConfigureListener"
	 * or "org.apache.myfaces.webapp.StartupServletContextListener" for the existed SUN RI or MyFaces implementation.
	 * This metod also calls appropriate {@link #setupSunFaces()} or {@link #setupMyFaces()} methods.
	 */
	protected void setupFacesListener() {
		
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! setupFacesListener");
		
		EventListener listener = null;
		try {
			// Check Sun RI configuration listener class.
			Class<? extends EventListener> listenerClass = contextClassLoader
					.loadClass("com.sun.faces.config.ConfigureListener")
					.asSubclass(EventListener.class);
			listener = listenerClass.getDeclaredConstructor().newInstance();
			setupSunFaces();
			
			System.out.println("SUNDONE");
		} catch (ClassNotFoundException e) {
			// No JSF RI listener, check MyFaces.
			Class<? extends EventListener> listenerClass;
			try {
				listenerClass = contextClassLoader
						.loadClass(
								"org.apache.myfaces.webapp.StartupServletContextListener")
						.asSubclass(EventListener.class);
				listener = listenerClass.getDeclaredConstructor().newInstance();
				setupMyFaces();
				System.out.println("MYFACESDONE");
			} catch (ClassNotFoundException e1) {
				throw new TestException("No JSF listeners have been found", e1);
			} catch (Exception e2) {
				throw new TestException("Error instantiate MyFaces listener",
						e2);
			}
		} catch (Exception e) {
			throw new TestException("Error instantiate JSF RI listener", e);
		}
		facesServer.addWebListener(listener);
	}

	/**
	 * This template method called from {@link #setUp()} to create {@link FacesServlet} instance.
	 * The default implementation also tests presense of the "org.ajax4jsf.Filter" class.
	 * If this class is avalable, these instance appended to the Faces Servlet call chain.
	 * Default mapping to the FacesServlet instance is "*.jsf"
	 */
	protected void setupFacesServlet() {
	    ServletHolder facesServletContainer = new ServletHolder("*.jsf", new FacesServlet());
		facesServletContainer.setName("Faces Servlet");
        facesServer.addServlet(facesServletContainer);
		try {
			// Check for an ajax4jsf filter.
			Class<? extends Filter> ajaxFilterClass = contextClassLoader
					.loadClass("org.ajax4jsf.Filter").asSubclass(Filter.class);
			Filter ajaxFilter = ajaxFilterClass.getDeclaredConstructor().newInstance();
			
			FilterHolder filterHolder = new FilterHolder("*.jsf", ajaxFilter);
			filterHolder.setName("ajax4jsf");
            facesServer.addResource("/WEB-INF/web.xml",
                "org/jboss/test/faces/ajax-web.xml");
			facesServer.addFilter(filterHolder);
		} catch (ClassNotFoundException e) {
			// No Richfaces filter, uses servlet directly.
			facesServer.addResource("/WEB-INF/web.xml",
					"org/jboss/test/faces/web.xml");
		} catch (Exception e) {
			throw new TestException(e);
		}
	}

	/**
	 * This template method called from {@link #setUp()} to append appropriate init parameters to the test server.
	 * The default implementation sets state saving method to the "server", default jsf page suffix to the ".xhtml"
	 * and project stage to UnitTest
	 */
	protected void setupJsfInitParameters() {
		facesServer.addInitParameter(
				StateManager.STATE_SAVING_METHOD_PARAM_NAME,
				StateManager.STATE_SAVING_METHOD_SERVER);
		facesServer.addInitParameter(ViewHandler.DEFAULT_SUFFIX_PARAM_NAME,
				".xhtml");
	
		facesServer.addInitParameter(ProjectStage.PROJECT_STAGE_PARAM_NAME, 
				ProjectStage.UnitTest.name());
	}

	/**
	 * This template method called from the {@link #setupFacesListener()} if MyFaces implementation presents.
	 * The default implementation does nothing.
	 */
	protected void setupMyFaces() {
		//MZ
    	facesServer.addInitParameter("org.apache.myfaces.INITIALIZE_ALWAYS_STANDALONE", "true");
    	facesServer.addInitParameter("org.apache.myfaces.FLASH_SCOPE_DISABLED", "true");		//TODO jakarta.servlet.SessionCookieConfig.getAttribute(String)" because the return value of "jakarta.servlet.ServletContext.getSessionCookieConfig()" is null
	}

	/**
	 * This template method called from the {@link #setupFacesListener()} if Sun JSF reference implementation presents.
	 * The default implementation sets the "com.sun.faces.validateXml" "com.sun.faces.verifyObjects" init parameters to the "true"
	 */
	protected void setupSunFaces() {
		facesServer.addInitParameter("com.sun.faces.validateXml", "true");
		facesServer.addInitParameter("com.sun.faces.verifyObjects", "true");
	}

	/**
	 * This template method called from the {@link #setUp()} to populate virtual server content.
	 * The default implementation tries to load web content from directory pointed by the System property
	 * "webroot" or same property from the "/webapp.properties" file.
	 */
	protected void setupWebContent() {
		String webappDirectory = System.getProperty("webroot");
        File webFile = null;
        if (null == webappDirectory) {
                URL resource = this.getClass().getResource("/webapp.properties");
                if (null != resource && "file".equals(resource.getProtocol())) {
                	Properties webProperties = new Properties();
                	try {
						InputStream inputStream = resource.openStream();
						webProperties.load(inputStream);
						inputStream.close();
	                    webFile = new File(resource.getPath());
	                    webFile = new File(webFile.getParentFile(), webProperties.getProperty("webroot")).getAbsoluteFile();
	            		facesServer.addResourcesFromDirectory("/", webFile);
					} catch (IOException e) {
						throw new TestException(e);
					}
                }
        } else {
                webFile = new File(webappDirectory);
        		facesServer.addResourcesFromDirectory("/", webFile);
        }

	}

	/**
	 * Setup virtual server connection to run tests inside JSF lifecycle.
	 * The default implementation setups virtual request to the "http://localhost/test.jsf" URL and creates {@link FacesContext} instance.
	 * Two template methods are called :
	 * <ol>
	 * <li>{@link #setupConnection()} to prepare request method, parameters, headers and so</li>
	 * <li>{@link #setupView()} to create default view.</li>
	 * </ol>
	 * @throws Exception
	 */
	protected void setupFacesRequest() throws Exception {
		String url = "http://localhost/test.jsf";
		setupFacesRequest(url);
		UIViewRoot viewRoot = setupView();
		if (null != viewRoot) {
			facesContext.setViewRoot(viewRoot);
		}
	}

	/**
	 * <p class="changed_added_2_0"></p>
	 * @param url
	 * @throws MalformedURLException
	 * @throws FacesException
	 */
	protected void setupFacesRequest(String url) throws MalformedURLException,
			FacesException {
		connection = facesServer.getConnection(new URL(
				url));
		setupConnection();
		connection.start();
		FacesContextFactory facesContextFactory = (FacesContextFactory) FactoryFinder
				.getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
		facesContext = facesContextFactory.getFacesContext(facesServer
				.getContext(), connection.getRequest(), connection
				.getResponse(), lifecycle);
	}

	/**
	 * This template method called from the {@link #setupFacesRequest()} to create components view tree in the virtual request. 
	 * The default implementation is only creates {@link UIViewRoot} instance for view ID "/test.xhtml".
	 * @return
	 */
	protected UIViewRoot setupView() {
		UIViewRoot viewRoot = (UIViewRoot) application.createComponent(UIViewRoot.COMPONENT_TYPE);
		viewRoot.setViewId("/test.xhtml");
		viewRoot.setLocale(Locale.getDefault());
		viewRoot.setRenderKitId(RenderKitFactory.HTML_BASIC_RENDER_KIT);
		return viewRoot;
	}

	/**
	 * This template method called from the {@link #setupFacesRequest()} to setup additional virtual connection parameters.
	 * The default implementation does nothing.
	 */
	protected void setupConnection() {

	}

	/**
	 * Virtual server instance cleanup.
	 * @throws java.lang.Exception
	 */
	@Override
    @After
	public void tearDown() throws Exception {
		if (null != facesContext) {
			facesContext.release();
			facesContext = null;
		}
		if (null != connection) {
			if (!connection.isFinished()) {
				connection.finish();
			}
			connection = null;
		}
		facesServer.destroy();
		Thread.currentThread().setContextClassLoader(contextClassLoader);
		facesServer = null;
		application = null;
		lifecycle = null;
	}

}
