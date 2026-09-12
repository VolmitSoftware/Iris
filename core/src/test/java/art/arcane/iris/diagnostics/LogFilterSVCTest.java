package art.arcane.iris.diagnostics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogFilterSVCTest {
    @Test
    public void removesFilterFromItsOriginalLoggerAfterLoggingShutdown() {
        Logger logger = mock(Logger.class);
        LoggerConfig configuration = mock(LoggerConfig.class);
        when(logger.get()).thenReturn(configuration);
        LogFilterSVC service = new LogFilterSVC();

        try (MockedStatic<LogManager> logging = mockStatic(LogManager.class)) {
            logging.when(LogManager::getRootLogger).thenReturn(logger);
            service.onEnable();
            service.onEnable();
            logging.when(LogManager::getRootLogger).thenThrow(new IllegalStateException("Logging stopped"));

            service.onDisable();
            service.onDisable();

            logging.verify(LogManager::getRootLogger, times(1));
            verify(configuration, times(1)).addFilter(service);
            verify(configuration, times(1)).removeFilter(service);
        }
    }

    @Test
    public void bindsToTheNewConfigurationAfterReenable() {
        Logger firstLogger = mock(Logger.class);
        Logger secondLogger = mock(Logger.class);
        LoggerConfig firstConfiguration = mock(LoggerConfig.class);
        LoggerConfig secondConfiguration = mock(LoggerConfig.class);
        when(firstLogger.get()).thenReturn(firstConfiguration);
        when(secondLogger.get()).thenReturn(secondConfiguration);
        LogFilterSVC service = new LogFilterSVC();

        try (MockedStatic<LogManager> logging = mockStatic(LogManager.class)) {
            logging.when(LogManager::getRootLogger).thenReturn(firstLogger, secondLogger);
            service.onEnable();
            service.onDisable();
            service.onEnable();
            service.onDisable();

            verify(firstConfiguration).addFilter(service);
            verify(firstConfiguration).removeFilter(service);
            verify(secondConfiguration).addFilter(service);
            verify(secondConfiguration).removeFilter(service);
        }
    }
}
