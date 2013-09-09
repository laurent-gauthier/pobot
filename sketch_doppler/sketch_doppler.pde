import org.pobot.sound.*;

int size_left, size_right;
int size_min = 63;
DopplerAnalyzer  recorder;
int THRESHOLD = 3000;
boolean detection = false;
int averages[];
int windows[][];
int position = 0;
int bin_count = 13;

// The statements in the setup() function 
// execute once when the program begins
void setup() {
  size(1280, 720);  // Size must be the first statement
  stroke(255);     // Set line drawing color to white
  averages = new int[bin_count];
  windows = new int[bin_count][10];
  size_left = size_min+100;
  size_right = size_min+100;
  frameRate(30);
  /* We are waiting for the user to press ENTER to
     start the recording. (You might find it
     inconvenient if recording starts immediately.)
  */
  recorder = new DopplerAnalyzer();
  recorder.start();
}

// The statements in draw() are executed until the 
// program is stopped. Each statement is executed in 
// sequence and after the last line is read, the first 
// line is executed again.
void draw() { 
  int i;
  background(0);   // Set the background to black
  int histogram[] = recorder.filter.histogram();
  int center = histogram[histogram.length/2]+histogram[histogram.length/2-1]+histogram[histogram.length/2+1];
  for(i=0;i<histogram.length;i++) {
    averages[i] += histogram[i] - windows[i][position];
    windows[i][position] = histogram[i];
  }
  position++;
  if (position == 10) { position = 0; }
  fill(102);
//  for(i=0;i<histogram.length;i++) {
//    if (i == histogram.length/2) {
//      rect(81+30*i, 720-(averages[i]+averages[i+1]+averages[i-1])/10, 20, (averages[i]+averages[i+1]+averages[i-1])/10);
//    } else {
//      rect(81+30*i, 720-averages[i]/10, 20, averages[i]/10);
//    }
//  }
  for(i=0;i<histogram.length;i++) {
    if (i == histogram.length/2) {
      rect(81+30*i, 720-100, 20, 100);
    } else {
      rect(81+30*i, 720-(histogram[i]*100)/(center+1), 20, (histogram[i]*100)/(center+1));
    }
  }
  //rect(640-81-(size_right+right/100), 81, size_right+right/100, size_right+right/100);
  println(frameRate);
}
