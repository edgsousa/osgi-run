package com.athaydes.gradle.osgi.testrun;

import com.athaydes.gradle.osgi.testrun.comm.RemoteOsgiTestRunner;
import com.athaydes.gradle.osgi.testrun.internal.RemoteOsgiRunTestRunnerClient;
import java.io.Closeable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * The osgi-run JUnit4 test runner.
 * <p>
 * Annotating a test class with the {@link org.junit.runner.RunWith} annotation
 * causes it to be tested within the OSGi test environment created by the osgi-run
 * Gradle plugin.
 * <p>
 * The test class may take any OSGi services it requires as parameters in its constructor.
 * Only one constructor is allowed.
 * <p>
 * This class is meant to be used by JUnit to run the tests outside OSGi. Do not use this class within OSGi.
 */
public class OsgiRunJUnit4TestRunner extends BlockJUnit4ClassRunner {

    private RemoteOsgiRunTestRunnerClient osgiTestRunnerClient;

    public OsgiRunJUnit4TestRunner( Class<?> testType ) throws InitializationError {
        super( testType );
    }

    private void initializeRunner() {
        if ( osgiTestRunnerClient == null ) {
            Iterator<RemoteOsgiRunTestRunnerClient> remoteTestRunners = ServiceLoader
                    .load( RemoteOsgiRunTestRunnerClient.class ).iterator();

            if ( remoteTestRunners.hasNext() ) {
                osgiTestRunnerClient = remoteTestRunners.next();
            } else {
                throw new RuntimeException( "Cannot find osgi-run Test Runner Service. Please add a jar that exports " +
                        "the " + RemoteOsgiTestRunner.class + " service to your test classpath" );
            }
        }
    }

    @Override
    protected void validateConstructor( List<Throwable> errors ) {
        initializeRunner();
        Optional<String> error = osgiTestRunnerClient.startTest( getTestClass().getName() );
        error.ifPresent( message -> errors.add( new Exception( message ) ) );
    }

    @Override
    protected void validateZeroArgConstructor( List<Throwable> errors ) {
        throw new UnsupportedOperationException( "OsgiRunJUnit4TestRunner supports tests with a non-zero args constructor" );
    }

    @Override
    protected Object createTest() throws Exception {
        return osgiTestRunnerClient.createTest( getTestClass().getName() );
    }

    @Override
    public void run( RunNotifier notifier ) {
        super.run( notifier );
        Optional.of( osgiTestRunnerClient ).ifPresent( ( o ) -> o.stopTest( getTestClass().getName() ) );
    }

    @Override
    protected Statement methodInvoker( FrameworkMethod method, Object test ) {
        Class<?> testInterface = findTestInterface( test );
        FrameworkMethod actualMethod = findMethodIn( testInterface, method );
        return new InvokeMethod( actualMethod, test );
    }

    private Class<?> findTestInterface( Object test ) {
        // the client test instance should implement the test interface and Closeable
        return Arrays.stream( test.getClass().getInterfaces() )
                .filter( definition -> !( definition.equals( Closeable.class ) ) )
                .findFirst().orElseThrow( () -> new IllegalStateException(
                        "Tried to call non-test method" ) );
    }

    private FrameworkMethod findMethodIn( Class<?> testClass, FrameworkMethod method ) {
        return new FrameworkMethod( Arrays.stream( testClass.getDeclaredMethods() )
                .filter( m -> m.getName().equals( method.getName() ) )
                .findFirst().orElseThrow( () -> new IllegalStateException(
                        "Tried to call non-test method" ) ) );
    }

}
