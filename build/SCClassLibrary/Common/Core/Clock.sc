// clocks for timing threads.

Clock {
	// abstract class
	*play { arg task;
		var beats, seconds;
		task.clock = this;
		seconds = thisThread.seconds;
		beats = this.secs2beats(seconds);
		this.sched(task.value(beats, seconds, this), task)
	}
}

SystemClock : Clock {
	*clear {
		_SystemClock_Clear
		^this.primitiveFailed
	}
	*sched { arg delta, item;
		_SystemClock_Sched
		^this.primitiveFailed
	}
	*schedAbs { arg time, item;
		_SystemClock_SchedAbs
		^this.primitiveFailed
	}
	
	*beats2secs { arg beats; ^beats }
	*secs2beats { arg secs; ^secs }
}


TempoClock : Clock {
	classvar all, <default;

	var queue, ptr;
	
	var <beatsPerBar=4.0, barsPerBeat=0.25;
	var baseBarBeat=0.0, baseBar=0.0;
	var <>permanent=false;

/*
You should only change the tempo 'now'. You can't set the tempo at some beat in the future or past, even though you might think so from the methods. 

There are several ideas of now:
	elapsed time, i.e. "real time"
	logical time in the current time base.
	logical time in another time base.

logical time is time that is incremented by exact amounts from the time you started. It is not affected by the actual time your task gets scheduled, which may shift around somewhat due to system load. By calculating using logical time instead of actual time, your process will not drift out of sync over long periods. every thread stores a clock and its current logical time in seconds and beats relative to that clock.

elapsed time is whatever the system clock says it is right now. elapsed time is always advancing. logical time only advances when your task yields or returns.

*/

	*new { arg tempo, beats, seconds;
		^super.new.init(tempo, beats, seconds)
	}
	
	*initClass {
		default = this.new.permanent_(true);
		CmdPeriod.add(this);
	}	
	
	*cmdPeriod {
		all.do({ arg item; item.clear });
		all.do({ arg item; if(item.permanent.not, { item.stop })  })
	}	

	init { arg tempo, beats, seconds;
		queue = Array.new(256);
		this.prStart(tempo, beats, seconds);
		all = all.add(this);
	}
	stop {
		all.take(this);
		this.prStop;
	}
	
	play { arg task, quant=1.0; this.schedAbs(this.elapsedBeats.roundUp(quant), task) }
	
	*play { ^this.shouldNotImplement(thisMethod) }
	
	tempo {
		_TempoClock_Tempo
		^this.primitiveFailed
	}
	beatDur {
		_TempoClock_BeatDur
		^this.primitiveFailed
	}
	elapsedBeats {
		_TempoClock_ElapsedBeats
		^this.primitiveFailed
	}

	sched { arg delta, item;
		_TempoClock_Sched
		^this.primitiveFailed
	}
	schedAbs { arg beat, item;
		_TempoClock_SchedAbs
		^this.primitiveFailed
	}
	clear {
		_TempoClock_Clear
		^this.primitiveFailed
	}

		
	// for setting the tempo at the current logical time 
	// (even another TempoClock's logical time).
	tempo_ { arg newTempo;
		if (thisThread.clock == this, {
			this.setTempoAtBeat(newTempo, thisThread.beats);
		},{
			this.setTempoAtSec(newTempo, thisThread.seconds);
		});
	}

	// for setting the tempo at the current elapsed time .
	etempo_ { arg newTempo;
		this.setTempoAtSec(newTempo, Main.elapsedTime);
	}

	beats2secs { arg beats; 
		_TempoClock_BeatsToSecs
		^this.primitiveFailed
	}
	secs2beats { arg secs; 
		_TempoClock_SecsToBeats
		^this.primitiveFailed
	}
	
	prDump { 
		_TempoClock_Dump
		^this.primitiveFailed
	}
	
	
	beats2bars { arg beats;
		^(beats - baseBarBeat) * barsPerBeat + baseBar;
	}
	bars2beats { arg bars;
		^(bars - baseBar) * beatsPerBar + baseBarBeat;
	}
	nextBar { arg beat;
		// given a number of beats, determine number beats at the next bar line.
		this.bars2beats(this.beats2bars(beat).ceil);
	}
	
	setMeterAtBar { arg newBeatsPerBar, bar;
		baseBarBeat = (bar - baseBar) * beatsPerBar + baseBarBeat;
		baseBar = bar;
		beatsPerBar = newBeatsPerBar;
		barsPerBeat = beatsPerBar.reciprocal;
		this.changed; 
	}
	setMeterAtBeat { arg newBeatsPerBar, beats;
		baseBar = (beats - baseBarBeat) * barsPerBeat + baseBar;
		baseBarBeat = beats;
		beatsPerBar = newBeatsPerBar;
		barsPerBeat = beatsPerBar.reciprocal;
		this.changed; 
	}
	
	
	// PRIVATE
	prStart { arg tempo;
		_TempoClock_New
		^this.primitiveFailed
	}
	prStop {
		_TempoClock_Free
		^this.primitiveFailed
	}
	setTempoAtBeat { arg newTempo, beats;
		_TempoClock_SetTempoAtBeat
		^this.primitiveFailed
	}
	setTempoAtSec { arg newTempo, secs;
		_TempoClock_SetTempoAtTime
		^this.primitiveFailed
	}
}


AppClock : Clock {
	classvar scheduler;
	*initClass {
		scheduler = Scheduler.new(this, true);
	}
	*sched { arg delta, item;
		scheduler.sched(delta, item)
	}
	*tick {
		scheduler.seconds = Main.elapsedTime;
	}
	*clear {
		scheduler.clear;
	}
	
	*beats2secs { arg beats; ^beats }
	*secs2beats { arg secs; ^secs }
}


Scheduler {
	var clock, drift, beats = 0.0, seconds = 0.0, queue;
	
	*new { arg clock, drift = false;
		^super.newCopyArgs(clock, drift).init;
	}
	init {
		beats = thisThread.beats;
		queue = PriorityQueue.new;
	}

	play { arg task;
		this.sched(task.value(thisThread.beats, thisThread.seconds, clock), task)
	}

	sched { arg delta, item;
		var fromTime;
		if (delta.notNil, { 
			fromTime = if (drift, { Main.elapsedTime },{ seconds });
			queue.put(fromTime + delta, item);
		});
	}
	clear { queue.clear }
	isEmpty { ^queue.isEmpty }
	
	advance { arg delta;
		this.seconds = seconds + delta;
	}
	
	seconds_ { arg newSeconds;
		var delta, item;
		while ({ 
			seconds = queue.topPriority; 
			seconds.notNil and: { seconds <= newSeconds }
		},{ 
			item = queue.pop;
			delta = item.awake( beats, seconds, clock );
			if (delta.isNumber, {
				this.sched(delta, item); 
			});
		});
		seconds = newSeconds;
		beats = clock.secs2beats(newSeconds);
	}
}

