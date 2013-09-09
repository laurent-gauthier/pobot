package org.pobot.sound;

import java.io.IOException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;


public class DopplerAnalyzer extends Thread {
	private TargetDataLine		m_line;
	private AudioInputStream	m_audioInputStream;
	short saved[];
	public SDFT_Filter filter;
	
	public DopplerAnalyzer() {
		/* For simplicity, the audio data format used for recording
		   is hardcoded here. We use PCM 44.1 kHz, 16 bit signed,
		   stereo.
		*/
		AudioFormat	audioFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			96000.0F, 16, 2, 4, 96000.0F, false);

		/* Now, we are trying to get a TargetDataLine. The
		   TargetDataLine is used later to read audio data from it.
		   If requesting the line was successful, we are opening
		   it (important!).
		*/
		DataLine.Info	info = new DataLine.Info(TargetDataLine.class, audioFormat);
		TargetDataLine	targetDataLine = null;
		try {
			Line.Info list[] = AudioSystem.getTargetLineInfo(info);
			System.out.println(" Infos: " + list.length);
			targetDataLine = (TargetDataLine) AudioSystem.getLine(list[0]);
			//targetDataLine = AudioSystem.getTargetDataLine(audioFormat);
			targetDataLine.open(audioFormat);
		} catch (LineUnavailableException e) {
			System.out.println("unable to get a recording line");
			e.printStackTrace();
			System.exit(1);
		}
		m_line = targetDataLine;
		m_audioInputStream = new AudioInputStream(m_line);
	}

	/** Starts the recording.
	    To accomplish this, (i) the line is started and (ii) the
	    thread is started.
	*/
	public void start()
	{
		/* Starting the TargetDataLine. It tells the line that
		   we now want to read data from it. If this method
		   isn't called, we won't
		   be able to read data from the line at all.
		*/
		m_line.start();
		setupSignal();

		/* Starting the thread. This call results in the
		   method 'run()' (see below) being called. There, the
		   data is actually read from the line.
		*/
		super.start();
	}


	/** Stops the recording.

	    Note that stopping the thread explicitely is not necessary. Once
	    no more data can be read from the TargetDataLine, no more data
	    be read from our AudioInputStream. And if there is no more
	    data from the AudioInputStream, the method 'AudioSystem.write()'
	    (called in 'run()' returns. Returning from 'AudioSystem.write()'
	    is followed by returning from 'run()', and thus, the thread
	    is terminated automatically.

	    It's not a good idea to call this method just 'stop()'
	    because stop() is a (deprecated) method of the class 'Thread'.
	    And we don't want to override this method.
	*/
	public void stopRecording()
	{
		m_line.stop();
		m_line.close();
	}


	public class SDFT_Filter {
		/* Implementation of the SDFT_ Algorithm.  */
		short left_window[];
		short right_window[];
		double re_left_filter[];
		double im_left_filter[];
		double re_right_filter[];
		double im_right_filter[];
		double re_left_filter2[];
		double im_left_filter2[];
		double re_right_filter2[];
		double im_right_filter2[];
		double cos_table[];
		double sin_table[];
		double average[];
		int size;
		int start;
		int end;
		int window_size;
		int position;
		
		SDFT_Filter(int size, int start, int end) {
			int i;
			left_window = new short[size];
			right_window = new short[size];
			for (i=0;i<window_size;i++) {
				left_window[i] = 0;
				right_window[i] = 0;
			}
			re_left_filter = new double[end-start+1];
			im_left_filter = new double[end-start+1];
			re_right_filter = new double[end-start+1];
			im_right_filter = new double[end-start+1];
			re_left_filter2 = new double[end-start+1];
			im_left_filter2 = new double[end-start+1];
			re_right_filter2 = new double[end-start+1];
			im_right_filter2 = new double[end-start+1];
			this.size = size;
			this.start = start;
			this.end = end;
			cos_table = new double[end-start+1];
			sin_table = new double[end-start+1];
			for(i=start;i<=end;i++) {
				cos_table[i-start] = Math.cos((2.0*Math.PI*i)/size);
				sin_table[i-start] = Math.sin((2.0*Math.PI*i)/size);
			}
			average = new double[end-start+1];
			position = 0;
		}
		
		/* Process a stereo buffer of samples.  */
		void process(short buffer[]) {
			int i, j;
			for(i=start;i<=end;i++) {
				average[i-start] = 0.0;
			}
			for(i=0;i<buffer.length;i+=2) {
				short left_sample = buffer[i];
				short right_sample = buffer[i+1];
				for(j=start;j<=end;j++) {
					double re, im;
					/* Complex math for right and left filter: filter = (filter - exiting_sample + incoming_sample) * e^((2*PI*FFT_bin)/FFT_size) */
					re = re_left_filter[j-start];
					im = im_left_filter[j-start];
					re_left_filter[j-start] = (re - left_window[position] + left_sample) * cos_table[j-start] - im * sin_table[j-start];
					im_left_filter[j-start] = (re - left_window[position] + left_sample) * sin_table[j-start] + im * cos_table[j-start];
					average[j-start] += Math.sqrt(re_left_filter[j-start]*re_left_filter[j-start]+im_left_filter[j-start]*im_left_filter[j-start]);

					re = re_right_filter[j-start];
					im = im_right_filter[j-start];
					re_right_filter[j-start] = (re - right_window[position] + right_sample) * cos_table[j-start] - im * sin_table[j-start];
					im_right_filter[j-start] = (re - right_window[position] + right_sample) * sin_table[j-start] + im * cos_table[j-start];
				}
				left_window[position] = left_sample;
				right_window[position] = right_sample;
				position++;
				if (position == size) { position = 0; }
			}
			for(i=start;i<=end;i++) {
				average[i-start] /= buffer.length;
			}
		}
		
		void show() {
			int i;
			int x[] = histogram();
			for(i=start;i<=end;i++) {
				System.out.print(" " + (x[i-start]));
			}
			System.out.println();
		}

		public int[] histogram() {
			int i;
			int x[] = new int[end-start+1];
			int sum = 0;
			int middle = (end-start+1)/2;
			double d = (Math.sqrt(re_left_filter[middle]*re_left_filter[middle]+im_left_filter[middle]*im_left_filter[middle]));
			for(i=start;i<=end;i++) {
				//x[i-start] = (int)average[i-start];
				x[i-start] = (int)(Math.sqrt(re_left_filter[i-start]*re_left_filter[i-start]+im_left_filter[i-start]*im_left_filter[i-start])*100.0/d);
				sum += x[i-start];
			}
			// int denom = x[4];
			// for(i=start;i<=end;i++) {
				// x[i-start] = x[i-start];
			// }
			return x;
		}
	}

	void setupSignal() {
		byte buffer[];
		AudioFormat	audioFormat = new AudioFormat(
										AudioFormat.Encoding.PCM_SIGNED,
										48000.0F, 16, 2, 4, 48000.0F, false);

		DataLine.Info info = new DataLine.Info(Clip.class, audioFormat);

		try {
			Clip audioClip = (Clip) AudioSystem.getLine(info);

//			audioClip.addLineListener(this);

			/* Setup to play a single tone signal.  */
			buffer = generateSingleTone();
			audioClip.open(audioFormat, buffer, 0, 48000);
			audioClip.loop(Clip.LOOP_CONTINUOUSLY);
			//audioClip.start();
			
		} catch (LineUnavailableException e) {
			System.out.println("unable to get a recording line");
			e.printStackTrace();
			System.exit(1);
		}

		//audioClip.close();
	}

	/* Generate the singletone data.  */
	byte[] generateSingleTone() {
		byte raw[];
		ByteBuffer buffer;
		short signal[];
		int duration = 4; // seconds
		double samplerate = 48000.0; // Hz
		int samples = (int)(duration*samplerate);
		double frequency1 = 16500.0; // Hz
		double period1 = samplerate / frequency1; // in sample points
		double omega1 = (Math.PI * 2.0) / period1;
		double volume = 62.0;
		int i;
		
		raw = new byte[samples*4];
		buffer = ByteBuffer.wrap(raw);
		signal = new short[samples*2];
		for(i=0;i<samples*2;i+=2) {
			signal[i] = (short)(volume*Math.sin(omega1*(i/2))); // Left
			signal[i+1] = (short)(volume*Math.sin(omega1*(i/2))); // Right
		}
		// Convert the array of short into an array of byte.
		buffer.asShortBuffer().put(signal);
		return raw;
	}

	/* This is where the doppler analysis is done.  */
	public void run()
	{
		byte[] input = new byte[12800]; /* 1/30s at 96000, stereo  */
		short[] stereo = new short[6400];
		filter = new SDFT_Filter(16384 /* size */, 2810 /* start */, 2822 /* end */);
		try
		{
			while (m_audioInputStream.read(input, 0, 12800) > 0) {
				ByteBuffer buffer = ByteBuffer.wrap(input);
				buffer.asShortBuffer().get(stereo);
				filter.process(stereo);
				//filter.show();
				saved = stereo;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		/* Now, we are creating an SimpleAudioProcessor object. It
		   contains the logic of starting and stopping the
		   recording, reading audio data from the TargetDataLine
		   and writing the data to a file.
		*/
		DopplerAnalyzer	recorder = new DopplerAnalyzer();

		/* We are waiting for the user to press ENTER to
		   start the recording. (You might find it
		   inconvenient if recording starts immediately.)
		*/
		recorder.start();

		/* And now, we are waiting again for the user to press ENTER,
		   this time to signal that the recording should be stopped.
		*/
		System.out.println("Analyzing...");
		System.out.println("Press ENTER to stop the processing.");
		try
		{
			System.in.read();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		/* Here, the recording is actually stopped.
		 */
		recorder.stopRecording();
		int i;
		for(i=0;i<recorder.saved.length;i+=2) {
			System.out.println(recorder.saved[i]);
		}
		System.out.println("Processing stopped.");
	}
}
