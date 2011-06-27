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

import java.util.HashSet;
import java.util.Set;

import uk.ac.imperial.presage2.core.Time;
import uk.ac.imperial.presage2.core.TimeDriven;
import uk.ac.imperial.presage2.core.participant.Participant;
import uk.ac.imperial.presage2.core.plugin.Plugin;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class ScenarioBuilder implements Scenario {

	public Injector injector;
	
	private Set<Participant> participants;
	
	private Set<Plugin> plugins;
	
	private Set<TimeDriven> timedriven;
	
	private Time finishTime;
	
	public ScenarioBuilder() {
		super();
		this.injector = null;
		this.participants = new HashSet<Participant>();
		this.plugins = new HashSet<Plugin>();
		this.timedriven = new HashSet<TimeDriven>();
		this.finishTime = null;
	}
	
	public ScenarioBuilder(AbstractModule module) {
		this.injector = Guice.createInjector(module);
		this.participants = new HashSet<Participant>();
		this.plugins = new HashSet<Plugin>();
		this.timedriven = new HashSet<TimeDriven>();
		this.finishTime = null;
		
		this.injector.injectMembers(this);
	}
	
	@Inject(optional=true)
	public void initialiseParticipants(Set<Participant> participants) {
		this.participants.addAll(participants);
	}
	
	@Inject(optional=true)
	public void initialisePlugins(Set<Plugin> plugins) {
		this.plugins.addAll(plugins);
	}
	
	@Inject(optional=true)
	public void initialiseTimeDriven(Set<TimeDriven> timedriven) {
		this.timedriven.addAll(timedriven);
	}
	
	@Inject(optional=true)
	public void initialiseFinishTime(@FinishTime Time finish) {
		this.finishTime = finish;
	}
	
	public void addParticipant(Participant p) {
		this.injector.injectMembers(p);
		this.participants.add(p);
	}
	
	public void addPlugin(Plugin p) {
		this.injector.injectMembers(p);
		this.plugins.add(p);
	}
	
	public void addTimeDriven(TimeDriven t) {
		this.injector.injectMembers(t);
		this.timedriven.add(t);
	}

	@Override
	public Set<Participant> getParticipants() {
		return this.participants;
	}

	@Override
	public Set<TimeDriven> getTimeDriven() {
		return this.timedriven;
	}

	@Override
	public Set<Plugin> getPlugins() {
		return this.plugins;
	}

	@Override
	public Time getFinishTime() {
		return this.finishTime;
	}

}