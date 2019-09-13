package de.gsi.dataset.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.dataset.spi.DefaultDataSet;

/**
 * Tests of DataSetLock
 * 
 * @author rstein
 * 
 * @see de.gsi.dataset.locks.DataSetLock
 * @see de.gsi.dataset.locks.DefaultDataSetLock
 */
public class DataSetLockTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataSetLockTest.class);

	/**
	 * Initalizes a lock and obtains a read lock.
	 * Then a thread is started which tries to obtain a write lock and blocks.
	 * Another thread then obtains a read lock, which succeeds.
	 * The main thread releases its read lock, upon which the writer thread also
	 * completes.
	 */
	@Test
	@DisplayName("Tests DefaultDataSetLock for interaction of write and read lock")
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void testDataSetLockReadWrite() {
        DefaultDataSet dataSet = new DefaultDataSet("test");
        DefaultDataSetLock<DefaultDataSet> myLockImpl = new DefaultDataSetLock<>(dataSet);
        DataSetLock<DefaultDataSet> myLock = myLockImpl;
        // assert initial state
        assertEquals(0, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
        assertTrue(dataSet.isAutoNotification());
        
        myLock.readLock().getDataCount();
        assertEquals(1, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
        
        myLock.readLock();
        assertEquals(2, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
        
        myLock.readUnLock();
        assertEquals(1, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
        
        Thread writer = new Thread( () ->  {
            myLock.writeLock().add(1, 2);
            assertEquals(0, myLockImpl.getReaderCount());
            assertEquals(1, myLockImpl.getWriterCount());
            myLock.writeUnLock();
            assertEquals(0, myLockImpl.getWriterCount());
        });
        writer.start();
        
        sleep(200);
        assertTrue(writer.isAlive());
        assertEquals(1, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
  
        Thread reader = new Thread( () -> {
            myLock.readLock().getDataCount();
            assertEquals(2, myLockImpl.getReaderCount());
            assertEquals(0, myLockImpl.getWriterCount());
            myLock.readUnLock();
        });
        reader.start();

        try {
            reader.join();
        } catch (InterruptedException e) {
            fail("ReaderThread was interupted");
        }
        assertEquals(1, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());

        assertTrue(writer.isAlive());
        myLock.readUnLock();
        
        sleep(200);

        assertFalse(writer.isAlive());
        assertEquals(0, myLockImpl.getReaderCount());
        assertEquals(0, myLockImpl.getWriterCount());
	}
	
	/**
	 * coarse tests N.B. to be refined
	 */
	@Test
	@DisplayName("Tests DataSet lock default implementation")
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	public void testDataSetLock() {
		DefaultDataSet dataSet = new DefaultDataSet("test");
		DefaultDataSetLock<DefaultDataSet> myLockImpl = new DefaultDataSetLock<>(dataSet);
		DataSetLock<DefaultDataSet> myLock = myLockImpl;

		// fluent-api style:
		// [..].writeLock().set(0, 0.0, 0.0).set(1, 1.0, 1.0).writeUnLock();

		assertEquals(0, myLockImpl.getReaderCount());
		assertEquals(0, myLockImpl.getWriterCount());
		assertTrue(dataSet.isAutoNotification());

		myLock.writeLock().getDataCount();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("WRITER: after first lock");
		}
		assertEquals(0, myLockImpl.getReaderCount());
		assertEquals(1, myLockImpl.getWriterCount());

		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("before having invoked first set of simple read[Lock,Unlock] threads");
		}
		final int nListener = 3;
		Thread[] threads = new Thread[nListener];
		for (int i = 0; i < 3; i++) {
			final int listener = i;
			threads[i] = new Thread(() -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().addArgument(listener).log("READER: try to acquire lock for listener #{}");
				}
				myLock.readLock();
				sleep(200);
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().addArgument(listener).log("READER: acquired lock for listener #{}");
				}
				myLock.readUnLock();
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().addArgument(listener).log("READER: released lock for listener #{}");
				}
			});
			threads[i].start();
		}

		// assertEquals(0, myLockImpl.getReaderCount());
		assertEquals(1, myLockImpl.getWriterCount());
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("after having invoked first set of simple read[Lock,Unlock] threads");
		}

		Thread anotherWriterThread = new Thread(() -> {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.atDebug().log("WRITER-#2: try to acquire write lock for another writer thread");
			}
			myLock.writeLock();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.atDebug().log("WRITER-#2: acquired write lock for another writer thread");
			}
			sleep(500);
			myLock.writeUnLock();
			if (LOGGER.isDebugEnabled()) {
				LOGGER.atDebug().log("WRITER-#2: released write lock for another writer thread");
			}
		});
		anotherWriterThread.start();

		assertEquals(1, myLockImpl.getWriterCount());

		myLock.writeLockGuard(() -> {
			sleep(100);
		});

		double retVal = myLock.writeLockGuard(() -> {
			sleep(100);
			return 2.0;
		});
		assertEquals(2.0, retVal);

		myLock.writeLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("WRITER: after second lock");
		}

		myLock.writeUnLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("WRITER: after second un-lock");
		}

		myLock.writeUnLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("WRITER: after first un-lock");
		}

		{
			// check read lockGuard
			boolean testState = myLock.readLockGuard(() -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().log("READLOCKGUARD-#1 - single guard");
				}
				return true;
			});
			assertTrue(testState);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.atDebug().log("after having invoked single read lock guard");
			}

			// check re-entrance of read lock guard
			testState = myLock.readLockGuard(() -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().log("READLOCKGUARD-#1 -- 1st run");
				}
				return myLock.readLockGuard(() -> {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.atDebug().log("\tREADLOCKGUARD-#2 -- 1st run");
					}
					return true;
				});
			});
			assertTrue(testState);
		}

		// recursive read locks
		myLock.readLock().getDataCount();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("READER: after first read lock");
		}

		myLock.readLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("READER: after second read lock");
		}

		myLock.readUnLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("READER: after second read unlock");
		}

		myLock.readUnLock();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("READER: after first read unlock");
		}

		{
			// re-check read lockGuard
			boolean testState = myLock.readLockGuard(() -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().log("READLOCKGUARD-#1 - single guard");
				}
				return true;
			});
			assertTrue(testState);

			// check re-entrance of read lock guard
			testState = myLock.readLockGuard(() -> {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.atDebug().log("READLOCKGUARD-#1 -- 2nd run");
				}
				return myLock.readLockGuard(() -> {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.atDebug().log("\tREADLOCKGUARD-#2 -- 2nd run");
					}
					return true;
				});
			});
			assertTrue(testState);
		}

		sleep(1000);
		assertEquals(0, myLockImpl.getReaderCount());
		assertEquals(0, myLockImpl.getWriterCount());
		assertTrue(dataSet.isAutoNotification());

		if (LOGGER.isDebugEnabled()) {
			LOGGER.atDebug().log("finished - testDataSetLock()");
		}
	}

	private static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			if (LOGGER.isErrorEnabled()) {
				LOGGER.atError().setCause(e).log("sleep error");
			}
			e.printStackTrace();
		}
	}
}
