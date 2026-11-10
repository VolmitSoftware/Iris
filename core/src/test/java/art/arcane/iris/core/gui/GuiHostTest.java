package art.arcane.iris.core.gui;

import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.desktop.QuitResponse;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GuiHostTest {
    @Test
    public void desktopQuitDisposesManagedWindowsWithoutStoppingServer() throws Exception {
        JFrame frame = mock(JFrame.class);
        ArgumentCaptor<WindowListener> listenerCaptor = ArgumentCaptor.forClass(WindowListener.class);
        GuiHost.prepareFrame(frame);
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger performed = new AtomicInteger();
        QuitResponse response = new QuitResponse() {
            @Override
            public void performQuit() {
                performed.incrementAndGet();
            }

            @Override
            public void cancelQuit() {
                cancelled.incrementAndGet();
            }
        };

        GuiHost.closeDesktopWindowsAndCancelQuit(response);
        SwingUtilities.invokeAndWait(() -> {
        });

        assertEquals(1, cancelled.get());
        assertEquals(0, performed.get());
        verify(frame).dispose();
        verify(frame, atLeastOnce()).addWindowListener(listenerCaptor.capture());
        listenerCaptor.getValue().windowClosed(mock(WindowEvent.class));
    }

    @Test
    public void closeWindowsDisposesManagedWindowsOnTheSwingThreadBeforeReturning() throws Exception {
        JFrame frame = mock(JFrame.class);
        AtomicBoolean disposedOnSwingThread = new AtomicBoolean();
        doAnswer(invocation -> {
            disposedOnSwingThread.set(EventQueue.isDispatchThread());
            return null;
        }).when(frame).dispose();
        ArgumentCaptor<WindowListener> listenerCaptor = ArgumentCaptor.forClass(WindowListener.class);
        GuiHost.prepareFrame(frame);

        GuiHost.closeWindows();

        verify(frame).dispose();
        assertTrue(disposedOnSwingThread.get());
        verify(frame, atLeastOnce()).addWindowListener(listenerCaptor.capture());
        listenerCaptor.getValue().windowClosed(mock(WindowEvent.class));
    }

    @Test
    public void closeWindowsWithoutWindowsReturnsImmediately() {
        GuiHost.closeWindows();
    }

    @Test
    public void preparedFramesDisposeWhenClosed() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicReference<JFrame> frameReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Iris lifecycle test");
            GuiHost.prepareFrame(frame);
            frame.setVisible(true);
            frameReference.set(frame);
        });

        JFrame frame = frameReference.get();
        assertEquals(JFrame.DISPOSE_ON_CLOSE, frame.getDefaultCloseOperation());
        assertTrue(frame.isDisplayable());
        SwingUtilities.invokeAndWait(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        assertFalse(frame.isDisplayable());
    }
}
