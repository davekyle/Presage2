/**
 * 	Copyright (C) 2011 Sam Macbeth <sm1106 [at] imperial [dot] ac [dot] uk>
 *
 * 	This file is part of Presage2.
 *
 *     Presage2 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Presage2 is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser Public License
 *     along with Presage2.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.imperial.presage2.core.simulator;

import java.util.concurrent.ExecutorService;

import org.apache.log4j.Logger;

import uk.ac.imperial.presage2.core.Time;
import uk.ac.imperial.presage2.core.TimeDriven;
import uk.ac.imperial.presage2.core.event.EventBus;
import uk.ac.imperial.presage2.core.participant.Participant;
import uk.ac.imperial.presage2.core.plugin.Plugin;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author Sam Macbeth
 * 
 */
@Singleton
public class MultiThreadedSimulator extends Simulator implements ThreadPool {

	private final Logger logger = Logger
			.getLogger(MultiThreadedSimulator.class);

	private final int threads;

	private final ExecutorServiceThreadPool threadPool;

	/**
	 * Constructor for use by Guice. Uses an injected {@link ThreadsValue}
	 * parameter to get a {@link Threads} value to pass to
	 * {@link #MultiThreadedSimulator(Scenario, Time, EventBus, int)}.
	 * 
	 * @param scenario
	 * @param t
	 * @param eventBus
	 * @param threads
	 */
	@Inject
	public MultiThreadedSimulator(Scenario scenario, Time t, EventBus eventBus,
			ThreadsValue threads) {
		this(scenario, t, eventBus, threads.value);
	}

	/**
	 * Create a multi threaded simulator for a given {@link Scenario}.
	 * 
	 * @param scenario
	 *            {@link Scenario} to simulate
	 * @param t
	 *            {@link Time} start time
	 * @param threads
	 *            Number of threads to use.
	 */
	public MultiThreadedSimulator(Scenario scenario, Time t, EventBus eventBus,
			int threads) {
		super(scenario, t, eventBus);
		this.threads = threads;
		this.threadPool = new ExecutorServiceThreadPool(this.threads);
	}

	/**
	 * Holder to allow optional injection of a {@link Threads} value, otherwise
	 * use a default value of 4.
	 * 
	 * @author Sam Macbeth
	 * 
	 */
	static class ThreadsValue {
		@Inject(optional = true)
		@Threads
		int value = 4;
	}

	/**
	 * <p>
	 * Wrapper for a call to {@link Participant#initialise()} as a
	 * {@link Runnable} for use with the {@link ExecutorService}.
	 * </p>
	 */
	private class ParticipantInitialisor implements Runnable {

		private final Participant p;

		public ParticipantInitialisor(Participant p) {
			this.p = p;
		}

		@Override
		public void run() {
			try {
				p.initialise();
			} catch (Exception e) {
				logger.warn("Exception thrown by participant " + p.getName()
						+ " on initialisation.", e);
			}
		}

	}

	/**
	 * <p>
	 * Wrapper for a call to {@link Plugin#initialise()} as a {@link Runnable}
	 * for use with the {@link ExecutorService}.
	 * </p>
	 */
	private class PluginInitialisor implements Runnable {

		private final Plugin p;

		public PluginInitialisor(Plugin p) {
			this.p = p;
		}

		@Override
		public void run() {
			try {
				p.initialise();
			} catch (Exception e) {
				logger.warn("Exception thrown by plugin " + p
						+ " on initialisation.", e);
			}
		}
	}

	@Override
	public void initialise() {

		// init Participants
		logger.info("Initialising Participants..");
		for (Participant p : this.scenario.getParticipants()) {
			submitScheduled(new ParticipantInitialisor(p),
					WaitCondition.END_OF_INITIALISE);
		}
		// init Plugins
		logger.info("Initialising Plugins..");
		for (Plugin pl : this.scenario.getPlugins()) {
			submitScheduled(new PluginInitialisor(pl),
					WaitCondition.END_OF_INITIALISE);
		}
		// wait for threads to complete
		waitFor(WaitCondition.END_OF_INITIALISE);
		eventBus.publish(Events.INITIALISED);
	}

	/**
	 * <p>
	 * Wrapper for a call to {@link TimeDriven#incrementTime()} as a
	 * {@link Runnable} for use with the {@link ExecutorService}.
	 * </p>
	 */
	private static class TimeIncrementor implements Runnable {

		private final TimeDriven t;

		public TimeIncrementor(TimeDriven t) {
			this.t = t;
		}

		@Override
		public void run() {
			t.incrementTime();
		}
	}

	/**
	 * Runs the simulation. We split the simulation cycle into two parts. We add
	 * all the participants to the thread pool, wait for them to finish
	 * executing and then run other {@link TimeDriven} elements and
	 * {@link Plugin}s.
	 */
	@Override
	public void run() {

		while (this.scenario.getFinishTime().greaterThan(time)) {

			logger.info("Time: " + time.toString());

			logger.info("Executing Participants...");
			for (Participant p : this.scenario.getParticipants()) {
				try {
					submitScheduled(new TimeIncrementor(p),
							WaitCondition.BEFORE_ENVIRONMENT);
				} catch (Exception e) {
					logger.warn(
							"Exception thrown by participant " + p.getName()
									+ " on execution.", e);
				}
			}

			logger.info("Executing TimeDriven...");
			for (TimeDriven t : this.scenario.getTimeDriven()) {
				try {
					submitScheduled(new TimeIncrementor(t),
							WaitCondition.END_OF_TIME_CYCLE);
				} catch (Exception e) {
					logger.warn("Exception thrown by TimeDriven " + t
							+ " on execution.", e);
				}
			}
			// wait for Participants to finish
			waitFor(WaitCondition.BEFORE_ENVIRONMENT);
			eventBus.publish(new ParticipantsComplete(time.clone()));

			try {
				submitScheduled(
						new TimeIncrementor(this.scenario.getEnvironment()),
						WaitCondition.END_OF_TIME_CYCLE);
			} catch (Exception e) {
				logger.warn(
						"Exception thrown by Environment "
								+ this.scenario.getEnvironment()
								+ " on execution.", e);
			}

			logger.info("Executing Plugins...");
			for (Plugin pl : this.scenario.getPlugins()) {
				try {
					submitScheduled(new TimeIncrementor(pl),
							WaitCondition.END_OF_TIME_CYCLE);
				} catch (Exception e) {
					logger.warn("Exception thrown by Plugin " + pl
							+ " on execution.", e);
				}
			}

			waitFor(WaitCondition.END_OF_TIME_CYCLE);
			eventBus.publish(new EndOfTimeCycle(time.clone()));

			time.increment();

		}

	}

	@Override
	public void complete() {
		logger.info("Running simulation completion tasks...");
		for (Plugin pl : this.scenario.getPlugins()) {
			try {
				pl.onSimulationComplete();
			} catch (Exception e) {
				logger.warn("Exception thrown by Plugin " + pl
						+ " on simulation completion.", e);
			}
		}
		eventBus.publish(new FinalizeEvent(time));
	}

	@Override
	public void submit(Runnable s) {
		threadPool.submit(s);
	}

	@Override
	public void submitScheduled(Runnable s, WaitCondition condition) {
		threadPool.submitScheduled(s, condition);
	}

	@Override
	public void waitFor(final WaitCondition condition) {
		threadPool.waitFor(condition);
	}

	@Override
	public void shutdown() {
		threadPool.shutdown();
	}

	@Override
	public int getThreadCount() {
		return this.threads;
	}

}
