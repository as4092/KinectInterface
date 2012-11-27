import processing.core.*; 

import processing.opengl.*; 
import SimpleOpenNI.*; 
import oscP5.*; 
import netP5.*; 

import java.applet.*; 
import java.awt.Dimension; 
import java.awt.Frame; 
import java.awt.event.MouseEvent; 
import java.awt.event.KeyEvent; 
import java.awt.event.FocusEvent; 
import java.awt.Image; 
import java.io.*; 
import java.net.*; 
import java.text.*; 
import java.util.*; 
import java.util.zip.*; 
import java.util.regex.*; 

public class UserScene3d_PlusSkeletonv5 extends PApplet {

 /* --------------------------------------------------------------------------
 * SimpleOpenNI UserScene3d Test
 * --------------------------------------------------------------------------
 * Processing Wrapper for the OpenNI/Kinect library
 * http://code.google.com/p/simple-openni
 * --------------------------------------------------------------------------
 * prog:  Max Rheiner / Interaction Design / zhkd / http://iad.zhdk.ch/
 * date:  02/16/2011 (m/d/y)
 * ----------------------------------------------------------------------------
 * Gonna try adding in continuous finger trackling, so once it's found it continues to track it
 * ----------------------------------------------------------------------------
 */
 
 





OscP5 oscP5;
NetAddress myRemoteLocation;
SimpleOpenNI context;

int currUser;
float        zoomF =0.5f;
float        rotX = radians(180);  // by default rotate the whole scene 180deg around the x-axis, 
// the data from openni comes upside down
float        rotY = radians(0);
//ALL GREEN NOW
int[]      userColors = { 
  color(0, 255, 0), color(0, 255, 0), color(0, 255, 00), color(0, 255, 0), color(0, 255, 0), color(0, 255, 0)
};
int[]      userCoMColors = { 
  color(255, 100, 100), color(100, 255, 100), color(100, 100, 255), color(255, 255, 100), color(255, 100, 255), color(100, 255, 255)
};
//Right Stuff
PVector head, rightHand, prevRightHand, rightWrist, rightElbow, rightShoulder, rightHip, rightThumb, rightPinky, prevRightThumb;
//Left Stuff
PVector leftHand, leftWrist, prevLeftHand, leftElbow, leftShoulder, leftHip, leftThumb, leftPinky, prevLeftThumb;
PVector[] rightFingers, leftFingers;

//LOOKS LIKE FIVE IS THE NORM MAX, SO LET'S DO SIX
int leftThumbNullCounter=0;
int rightThumbNullCounter=0;
int closedLevel=4;
int openLevel=-4;
BoundingBox bBoxLeft, bBoxRight;

int gestureCounter=0;

GestureController gc;

boolean leftWristSet, rightWristSet, leftHandOpen, rightHandOpen;
float savedZ, savedUMag, savedLMag;
PImage lHand, rHand;
PFrame f, f2,f3;

public void setup() {
  size(1024, 768, P3D); 
  f=new PFrame("Left Hand",200,400);
  f2=new PFrame("Right Hand",200,400);
  f3=new PFrame("Text Stuff",400,200);
  PFont font;
  font = loadFont("Serif-26.vlw"); 
  f3.s.textFont(font); 
  
  
  context = new SimpleOpenNI(this);

  rightWrist= new PVector();
  leftWristSet=false;
  rightWristSet=false;
  savedUMag=0.0f;
  savedZ=0.0f;

  // disable mirror
  context.setMirror(false);

  // enable depthMap generation 
  if (context.enableDepth() == false)
  {
    println("Can't open the depthMap, maybe the camera is not connected!");
    exit();
    return;
  }

  context.enableDepth();

  // enable skeleton generation for all joints
  context.enableUser(SimpleOpenNI.SKEL_PROFILE_ALL);
  
  // start oscP5, telling it to listen for incoming messages at port 5001 */
  oscP5 = new OscP5(this,50001);
 
  // set the remote location to be the localhost on port 5001
  myRemoteLocation = new NetAddress("127.0.0.1",57131);

  strokeWeight(10);
  stroke(255, 255, 255);
  smooth();  

  perspective(radians(45), 
  PApplet.parseFloat(width)/PApplet.parseFloat(height), 
  10, 150000);
  
}


public void draw()
{
  // update the cam
  context.update();
  background(0, 0, 0);

  pushMatrix();
  //Gets the skeleton out of the way
  translate(-1*width/2, height/2);
  //print("First translate: "+ width/2 +", "+height/2);
  if(rightHand!=null){
    if(gestureCounter<3){
      gestureCounter++;
    }
    else{
      gestureCounter=0;
      PVector[] skeleton= new PVector[11];
      skeleton[0]=head;
      skeleton[1]=rightHand;
      skeleton[2]=rightWrist;
      skeleton[3]=rightElbow;
      skeleton[4]=rightShoulder;
      skeleton[5]=rightHip;
      skeleton[6]=leftHand;
      skeleton[7]=leftWrist;
      skeleton[8]=leftElbow;
      skeleton[9]=leftShoulder;
      skeleton[10]=leftHip;
      
      if(gc==null){
        gc=new GestureController(skeleton);
      }
      else{
        
        String[] gestures = gc.update(skeleton);
        
        String forPrinting = "This time I saw: ";
        
        f3.s.fill(0);
        f3.s.rect(0,0,400,200);
        
        for(int i = 0; i<gestures.length; i++){
          if(gestures[i]!=null){
            forPrinting+= gestures[i]+", ";
          }
        }
       
       f3.s.fill(255);
       
       f3.s.text(forPrinting,5,5,300,150); 
       
      }
    }
  }

  // for all users from 1 to 10
  int i;
  for (i=1; i<=10; i++)
  {
    // check if the skeleton is being tracked
    if (context.isTrackingSkeleton(i))
    {

      // draw the skeleton
      drawSkeleton(i);  

      // draw a circle for a head 
      circleForAHead(i);

      // draw a circle for left hand
      circleForLeftHand(i);

      // draw a circle for right hand
      circleForRightHand(i);
      //
      sendJointPosition(i);
    }
  }

  
  popMatrix();
  pushMatrix();
  establishLeftBox();
  establishRightBox();
  
  // set the scene pos
  translate(width/2, height/2, 0);
  //print("Second translate: "+ width/2 +", "+height/2);
  rotateX(rotX);
  rotateY(rotY);
  scale(zoomF);

  int[]   depthMap = context.depthMap();
  int     steps   = 3;  // to speed up the drawing, draw every third point
  int     index;
  PVector realWorldPoint;

  translate(0, 0, -1000);  // set the rotation center of the scene 1000 infront of the camera

  int userCount = context.getNumberOfUsers();
  int[] userMap = null;
  if (userCount > 0)
  {
    userMap = context.getUsersPixels(SimpleOpenNI.USERS_ALL);
  }

  for (int y=0;y < context.depthHeight();y+=steps)
  {
    for (int x=0;x < context.depthWidth();x+=steps)
    {
      index = x + y * context.depthWidth();
      if (depthMap[index] > 0)
      { 
        // get the realworld points
        realWorldPoint = context.depthMapRealWorld()[index];

        // check if there is a user
        if (userMap != null && userMap[index] != 0)
        {
          if(bBoxRight!=null && bBoxLeft!=null){
            
            if (bBoxRight.contains(0, 0, realWorldPoint.x, realWorldPoint.y)) {
              if (!excludeFromRight(realWorldPoint)) {
                stroke(255);
                point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                if (checkProximity(realWorldPoint, rightHand, 10.0f)) {
                  stroke(255, 0, 0);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
                //TESTING, ONE MORE TIME
                if (checkProximity(realWorldPoint, rightWrist, 30.0f)) {
                  stroke(0, 0, 255);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
              }
            }
            else if (bBoxLeft.contains(0, 0, realWorldPoint.x, realWorldPoint.y)) {
              if (!excludeFromLeft(realWorldPoint)) {
                stroke(255);
                point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                if (checkProximity(realWorldPoint, leftHand, 10.0f)) {
                  stroke(255, 0, 0);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
                if (checkProximity(realWorldPoint, leftWrist, 30.0f)) {
                  stroke(0, 0, 255);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
              }
            }
            else {
              int colorIndex = userMap[index] % userColors.length;
              //stroke(255);
              stroke(userColors[colorIndex]); 
              point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
            }
            
          }
          
          else if (bBoxRight!=null) {
           
            if (bBoxRight.contains(0, 0, realWorldPoint.x, realWorldPoint.y)) {
              if (!excludeFromRight(realWorldPoint)) {
                stroke(255);
                point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                if (checkProximity(realWorldPoint, rightHand, 10.0f)) {
                  stroke(255, 0, 0);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
                
                
                if (checkProximity(realWorldPoint, rightWrist, 10.0f)) {
                  stroke(0, 0, 255);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
              }
            }
            else {
              int colorIndex = userMap[index] % userColors.length;
              //stroke(255);
              stroke(userColors[colorIndex]); 
              point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
            }
          }
          else if (bBoxLeft!=null) {
            
            if (bBoxLeft.contains(0, 0, realWorldPoint.x, realWorldPoint.y)) {
              if (!excludeFromLeft(realWorldPoint)) {
                stroke(255);
                point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                if (checkProximity(realWorldPoint, leftHand, 10.0f)) {
                  stroke(255, 0, 0);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
                if (checkProximity(realWorldPoint, leftWrist, 10.0f)) {
                  stroke(0, 0, 255);
                  point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
                }
              }
            }
            else {
              int colorIndex = userMap[index] % userColors.length;
              //stroke(255);
              stroke(userColors[colorIndex]); 
              point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
            }
          }
          else {
            int colorIndex = userMap[index] % userColors.length;
            //stroke(255);
            stroke(userColors[colorIndex]); 
            point(realWorldPoint.x, realWorldPoint.y, realWorldPoint.z);
          }
        }
      }
    }
  } 

  // draw the center of mass
  /**
  //TRYING: Don't really need this
  PVector pos = new PVector();
  pushStyle();
  strokeWeight(15);
  for (int userId=1;userId <= userCount;userId++)
  {
    context.getCoM(userId, pos);

    stroke(userCoMColors[userId % userCoMColors.length]);
    point(pos.x, pos.y, pos.z);
  }  
  popStyle();
  popMatrix();
  */
  pushStyle();
  
  if(rightHand!=null && leftHand!=null){
    if(leftHand.x<leftShoulder.x || leftHand.x>rightShoulder.x || leftHand.x<-1000 || leftHand.x>1000){
      leftHandBother();
    }
    if(rightHand.x<leftShoulder.x || rightHand.x>rightShoulder.x || rightHand.x<-1000 || rightHand.x>1000){
      rightHandBother();
    }
  }
  popStyle();
  
  prevRightHand=rightHand;
  prevLeftHand=leftHand;
  
  if(rightThumb!=null){
    prevRightThumb=rightThumb;
  }
  
  if(leftThumb!=null){
    prevLeftThumb=leftThumb;
  }
  if(leftThumbNullCounter==closedLevel){
    prevLeftThumb=null;
    leftHandOpen=false;
  }
  else if(leftThumbNullCounter==openLevel){
    leftHandOpen=true;
  }
  if(rightThumbNullCounter==closedLevel){
    prevRightThumb=null;
    rightHandOpen=false;
  }
  else if(rightThumbNullCounter==openLevel){
    rightHandOpen=true;
  }
  
}

public void rightHandBother(){
  //ALL THE LEFT HAND IMAGE STUFFS
  if (bBoxRight!=null) {
    translate(screenWidth/8, screenHeight/8);
    float minX = bBoxRight.getMinX();
    float minY = bBoxRight.getMaxY();

    PVector minVec = new PVector(minX, minY, rightElbow.z);

    float maxX = bBoxRight.getMaxX();
    float maxY = bBoxRight.getMinY();

    PVector maxVec = new PVector(maxX, maxY, rightElbow.z);

    PVector minPos_Proj = new PVector(); 
    context.convertRealWorldToProjective(minVec, minPos_Proj);

    PVector maxPos_Proj = new PVector(); 
    context.convertRealWorldToProjective(maxVec, maxPos_Proj);

    PVector rightHand_Proj = new PVector();
    context.convertRealWorldToProjective(rightHand, rightHand_Proj);

    rightHand_Proj.x+=screenWidth/8;
    rightHand_Proj.y+=screenHeight/8;

    int imgW = (int)maxPos_Proj.x-(int)minPos_Proj.x;
    int imgH = (int) maxPos_Proj.y-(int)minPos_Proj.y;

    //Turn on smoothing
    smooth();

    //Safeguard in case of bad calculations
    if((int)minPos_Proj.x+screenWidth/8>0 && ((int)minPos_Proj.x+screenWidth/8+(imgW*2))<screenWidth){
      rHand = get((int)minPos_Proj.x+screenWidth/8, (int)minPos_Proj.y+screenHeight/8, imgW*2, imgH*2);
    }
    PImage rightHand = rHand;

    boolean firstRed=false;
    rightHand.loadPixels();

    int handPosX0=0;
    int handPosY0=0;

    boolean firstBlue =false;
    PVector wrist = new PVector(0, 0);

    //Grabs the first red (palm) and first blue (wrist) positions
    for (int y=0; y<rightHand.height; y++) {
      for (int x=0; x<rightHand.width; x++) {
        int ind = x + y * rightHand.width;
        if(ind<rightHand.pixels.length){
          if (red(rightHand.pixels[ind])== 255 && blue(rightHand.pixels[ind])==0) {
            if (!firstRed) {
              handPosX0=x;
              handPosY0=y;
              firstRed=true;
            }
          }
          //If blue
          if (blue(rightHand.pixels[ind])== 255 && red(rightHand.pixels[ind])==0) {
            if (!firstBlue) {
              wrist = new PVector(x, y);
              firstBlue=true;
            }
          }
        }
      }
    }
    
    PVector hand = new PVector(handPosX0, handPosY0);
    
    
    //dPVector translation = new PVector((int)minPos_Proj.x+screenWidth/8, (int)minPos_Proj.y+screenHeight/8);
    
    PVector prevPinky = null;
    ///if(rightPinky!=null){
    //  prevPinky = PVector.sub(rightPinky, translation);
    //}
    
    //WARNING, MIGHT NEED SOMETHING FOR THE ABOVE FOR THE PREVIOUS FINGERS AT SOME POINT
    f2.s.fd = new FingerDetector(rightHand, hand, wrist, prevRightThumb, prevPinky, rightFingers);
    PImage newRight = f2.s.fd.getImage();

    //Clear screen
    f2.s.fill(0);
    f2.s.rect(0, 0, 400, 400);
    //Add in the image
    f2.s.image(newRight, 0, 0);

    PVector thumb = f2.s.fd.thumbDetection();
    
    //TESTING: See if this gets rid of the thumbs when closed
    if(!f2.s.fd.inThumbRange(thumb)){
      thumb=null;
    }
    PVector testThumb = null;
    
    //PVector fdToScreenDifference = new PVector(minPos_Proj.x+(screenWidth/8), minPos_Proj.y+(screenHeight/8),0);
    //boolean firstFailed=false;
      
      if (thumb!=null){
          if(thumb.x>0){
            //println("In if, Left thumb was: "+thumb);
            //TESTING
            if(f2.s.fd.thumbChecker(thumb)){
              f2.s.fill(0, 200, 0);
              f2.s.ellipse(thumb.x, thumb.y, 15, 15);
              //println("Possibility 1 Right: "+thumb);
            
              testThumb=thumb;
              
              
              //Convert thumb to projective space
             // PVector handThumbDiff = PVector.sub(thumb, f.s.fd.hand);
             // PVector leftThumbProj = PVector.add(this.leftHand, handThumbDiff);
              rightThumb=thumb;
            }
        }
      }
    
    if(testThumb==null){
      if(rightThumbNullCounter<closedLevel){
        rightThumbNullCounter++;
      }
     // println("Right Null this time");
      
    }
    else{
      if(rightThumbNullCounter>openLevel){
        rightThumbNullCounter--;
      }
    }

    PVector pinky = f2.s.fd.getPinky();
    if (pinky!=null) {
      f2.s.fill(255, 255, 0);
      f2.s.ellipse(pinky.x, pinky.y, 15, 15);
      
      rightPinky= new PVector((int)minPos_Proj.x+(screenWidth/8)+pinky.x, (int)minPos_Proj.y+(screenHeight/8)+pinky.y);
    }
    
    

    if (thumb!=null) {
      PVector[] fingerz = f2.s.fd.pickOutFingers();
      f2.s.fill(200, 100, 100);
      //Makes little circles for the fingers
      for (int q =0; q<fingerz.length; q++) {
        if (fingerz[q]!=null) {
          f2.s.ellipse(fingerz[q].x, fingerz[q].y, 15, 15);
        }
      }
      rightFingers = fingerz;
    }
  }    
}

public void leftHandBother(){
  //ALL THE LEFT HAND IMAGE STUFFS
  if (bBoxLeft!=null) {
    translate(screenWidth/8, screenHeight/8);
    float minX = bBoxLeft.getMinX();
    float minY = bBoxLeft.getMaxY();

    PVector minVec = new PVector(minX, minY, leftElbow.z);

    float maxX = bBoxLeft.getMaxX();
    float maxY = bBoxLeft.getMinY();

    PVector maxVec = new PVector(maxX, maxY, leftElbow.z);

    PVector minPos_Proj = new PVector(); 
    context.convertRealWorldToProjective(minVec, minPos_Proj);

    PVector maxPos_Proj = new PVector(); 
    context.convertRealWorldToProjective(maxVec, maxPos_Proj);

    PVector leftHand_Proj = new PVector();
    context.convertRealWorldToProjective(leftHand, leftHand_Proj);

    leftHand_Proj.x+=screenWidth/8;
    leftHand_Proj.y+=screenHeight/8;

    int imgW = (int)maxPos_Proj.x-(int)minPos_Proj.x;
    int imgH = (int) maxPos_Proj.y-(int)minPos_Proj.y;


    //WARNING: This here is a problem, but I don't know how exactly to fix
    if(imgW>10 && imgH>10){
      lHand = get((int)minPos_Proj.x+screenWidth/16, (int)minPos_Proj.y+screenHeight/8, imgW*2+screenWidth/16, imgH*3);
    }
    //Assums leftHand.x is further right than the left edge

    PImage leftHand = lHand;

    boolean firstRed=false;
    leftHand.loadPixels();

    int handPosX0=0;
    int handPosY0=0;

    boolean firstBlue =false;
    PVector wrist = null;

    PVector hand = null;
  
    //Grabs the first red (palm) and first blue (wrist) positions
    for (int y=0; y<leftHand.height; y++) {
      for (int x=0; x<leftHand.width; x++) {
        int ind = x + y * leftHand.width;
        if(ind>0 && ind<leftHand.pixels.length){
          if (red(leftHand.pixels[ind])== 255 && blue(leftHand.pixels[ind])==0) {
            if (!firstRed) {
              hand = new PVector(x,y);
              firstRed=true;
            }
          }
          //If blue
          if (blue(leftHand.pixels[ind])== 255 && red(leftHand.pixels[ind])==0) {
            if (!firstBlue) {
              wrist = new PVector(x, y);
              firstBlue=true;
            }
          }
        }
      }
    }
    PVector translation = new PVector((int)minPos_Proj.x, (int)minPos_Proj.y+screenHeight/8);
    
    PVector prevThumb =null;
    if(prevLeftThumb!=null){
      //TRYING: TO GET RID OF TRANSLATING IT AT ALL
      prevThumb = prevLeftThumb;//PVector.sub(leftThumb, translation);
    }
    PVector prevPinky = null;
    if(leftPinky!=null){
      prevPinky = leftPinky;//PVector.sub(leftPinky, translation);
    }
    
    //WARNING: MIGHT NEED SOMETHING LIKE THE ABOVE FOR THE PREVIOUS FINGERS, POTENTIALLY
    f.s.fd = new FingerDetector(leftHand, hand, wrist, prevThumb, prevPinky, leftFingers);

    //Purely for me to say it's working
    PImage newLeft = f.s.fd.getImage();

    //Clear screen
    f.s.fill(0);
    f.s.rect(0, 0, 400, 400);
    //Add in the image
    f.s.image(newLeft, 0, 0);

    PVector thumb = f.s.fd.thumbDetection();
    
    //TESTING: See if this gets rid of the thumbs when closed
    if(!f.s.fd.inThumbRange(thumb)){
      thumb=null;
    }
    PVector testThumb = null;
    
    //PVector fdToScreenDifference = new PVector(minPos_Proj.x+(screenWidth/8), minPos_Proj.y+(screenHeight/8),0);
    //boolean firstFailed=false;
      
      if (thumb!=null){
          if(thumb.x>0){
            //println("In if, Left thumb was: "+thumb);
            //TESTING
            if(f.s.fd.thumbChecker(thumb)){
              f.s.fill(0, 200, 0);
              f.s.ellipse(thumb.x, thumb.y, 15, 15);
              //println("Possibility 1 Left: "+thumb);
              
              testThumb=thumb;
              
              //Convert thumb to projective space
             // PVector handThumbDiff = PVector.sub(thumb, f.s.fd.hand);
             // PVector leftThumbProj = PVector.add(this.leftHand, handThumbDiff);
              leftThumb=thumb;
            }
        }
      }
      
    if(testThumb==null){
      if(leftThumbNullCounter<closedLevel){
        leftThumbNullCounter++;
      }
    }
    else{
      if(leftThumbNullCounter>openLevel){
        leftThumbNullCounter--;
      }
    }
   
    PVector pinky = f.s.fd.getPinky();
    
    if (pinky!=null) {
      f.s.fill(255, 255, 0);
      f.s.ellipse(pinky.x, pinky.y, 15, 15);
      leftPinky = new PVector((int)minPos_Proj.x+(screenWidth/8)+pinky.x, (int)minPos_Proj.y+(screenHeight/8)+pinky.y);
    }

    if (testThumb!=null) {
      PVector[] fingerz = f.s.fd.pickOutFingers();
      f.s.fill(200, 100, 100);
      //Makes little circles for the fingers
      for (int q =0; q<fingerz.length; q++) {
        if (fingerz[q]!=null) {
          f.s.ellipse(fingerz[q].x, fingerz[q].y, 15, 15);
          
        }
      }
      leftFingers=fingerz;
    }    
  }    
}


//Determines whether pixel should be excluded from right hand
public boolean excludeFromRight(PVector pixel) {
  boolean exclude = false;
  //Shoulder and hip
  if (checkProximity(pixel, rightShoulder, 40.0f) || checkProximity(pixel, rightHip, 40.0f)) {
    exclude=true;
  }

  if (rightShoulder.x>pixel.x) {
    exclude=true;
  }

  return exclude;
}

//Determines whether pixel should be excluded from left hand
public boolean excludeFromLeft(PVector pixel) {
  boolean exclude = false;
  //Shoulder and hip
  if (checkProximity(pixel, leftShoulder, 40.0f) || checkProximity(pixel, leftHip, 40.0f)) {
    exclude=true;
  }

  if (leftShoulder.x<pixel.x) {
    exclude=true;
  }

  return exclude;
}


//Find everything that needs replacing
public boolean checkProximity(PVector pixel, PVector check, float distWithin) {
  float measure = distWithin;
  boolean closeEnough = false;
  PVector h = check;
  if (h!=null) {
    if (h.x+measure> pixel.x && h.x-measure<pixel.x) {
      if (h.y+measure>pixel.y && h.y-measure<pixel.y) {
        closeEnough=true;
      }
    }
  }

  return closeEnough;
}

public void drawCircleForWrist(PVector wristPos) {
  //DRAWING THE WRIST
  // convert real world point to projective space
  PVector jointPos_Proj = new PVector(); 
  context.convertRealWorldToProjective(wristPos, jointPos_Proj);
  wristPos.z=0;

  fill(0, 0, 255);
  // draw the circle at the position of the head with the hand size scaled by the distance scalar
  ellipse(jointPos_Proj.x, jointPos_Proj.y, 50, 50);
}

public void establishLeftBox() {
  float measure = 40.0f;
  boolean closeEnough = false;
  PVector h = leftHand;
  PVector e = leftElbow;
  PVector s = leftShoulder;

  if (h!=null) {
    //Figures out the initial wrist values
    if (!leftWristSet) {
      PVector upper=PVector.sub(e, s);
      savedUMag=upper.mag();

      PVector lower = PVector.sub(e, h);
      savedLMag=lower.mag();

      savedZ=(s.z);

      leftWristSet=true;
    }

    PVector wristPos = findWrist(h, e, s);
    leftWrist=wristPos;
    wristPos.z= leftHand.z;


    if (wristPos!=null) {
      //Try adding this in
      //wristPos.z=0;

      //Calculate edge of hand
      PVector h2 =PVector.sub(h, wristPos);

      //Try getting rid of z here as well
      //h2.z=0;
      h2.mult(3);

      PVector handLength = h2;
      h2.add(wristPos);

      //println("WristPos: "+wristPos);
      //println("h2 :" +h2); 

      PVector one = PVector.sub(wristPos, h2);
      //0.4 is a bit big, 0.25 is a bit small
      one.mult(0.35f);
      float orig1X = one.x;
      float orig1Y = one.y;


      one.x=-1*orig1Y;
      one.y=orig1X;


      PVector two=new PVector(one.x, one.y, one.z);
      one.add(wristPos);
      two.mult(-1);

      two.add(wristPos);

      PVector four = PVector.sub(h2, wristPos);
      //
      four.mult(0.35f);

      float orig4X = four.x;
      float orig4Y=four.y;
      four.x=orig4Y;
      four.y=-1*orig4X;

      PVector three = new PVector(four.x, four.y, four.z);

      four.add(h2);
      three.mult(-1);
      three.add(h2);

      //Let's hope these all aren't the same
      //println("One: "+ one);
      //println("Two: "+two);
      //println("Three: "+three);
      //println("Four:" + four);
      //println("");
      bBoxLeft = new BoundingBox(one, two, three, four);
    }
  }
}

public void establishRightBox() {
  float measure = 40.0f;
  boolean closeEnough = false;
  PVector h = rightHand;
  PVector e = rightElbow;
  PVector s = rightShoulder;

  if (h!=null) {
    //Figures out the initial wrist values
    if (!rightWristSet) {
      PVector upper=PVector.sub(e, s);
      savedUMag=upper.mag();

      PVector lower = PVector.sub(e, h);
      savedLMag=lower.mag();

      savedZ=(s.z);

      rightWristSet=true;
    }
    
    PVector wristPos = new PVector();
    wristPos=findWrist(h, e, s);
    rightWrist=wristPos;
    wristPos.z= rightHand.z;


    if (wristPos!=null) {
      //Try adding this in
      //wristPos.z=0;

      //Calculate edge of hand
      PVector h2 =PVector.sub(h, wristPos);

      //Try getting rid of z here as well
      //h2.z=0;
      h2.mult(3);

      PVector handLength = h2;
      h2.add(wristPos);

      //println("WristPos: "+wristPos);
      //println("h2 :" +h2); 

      PVector one = PVector.sub(wristPos, h2);
      //0.4 is a bit big, 0.25 is a bit small
      one.mult(0.35f);
      float orig1X = one.x;
      float orig1Y = one.y;


      one.x=-1*orig1Y;
      one.y=orig1X;


      PVector two=new PVector(one.x, one.y, one.z);
      one.add(wristPos);
      two.mult(-1);

      two.add(wristPos);

      PVector four = PVector.sub(h2, wristPos);
      //
      four.mult(0.35f);

      float orig4X = four.x;
      float orig4Y=four.y;
      four.x=orig4Y;
      four.y=-1*orig4X;

      PVector three = new PVector(four.x, four.y, four.z);

      four.add(h2);
      three.mult(-1);
      three.add(h2);

      //Let's hope these all aren't the same
      //println("One: "+ one);
      //println("Two: "+two);
      //println("Three: "+three);
      //println("Four:" + four);
      //println("");
      bBoxRight = new BoundingBox(one, two, three, four);
    }
  }
}

public boolean checkLeftExtension() {
  PVector h = leftHand;
  PVector e = leftElbow;
  PVector s = leftShoulder;

  PVector upper = PVector.sub(e, s);
  PVector lower = PVector.sub(e, h);

  float newUMag = upper.mag();
  float newLMag = lower.mag();

  //Original value to check against
  float check=savedLMag/savedUMag;
  check = check/savedZ;

  //New value to check against the original
  float currValue = newLMag/newUMag;
  currValue = currValue/s.z;


  if (currValue>check) {
    return true;
  }
  else {
    return false;
  }
}


public PVector findWrist(PVector h, PVector e, PVector s) {
  PVector wristPos=null;
  PVector upperArm = PVector.sub(e, s);
  //println("Upper arm length: "+upperArm.mag());

  PVector lowerArm = PVector.sub(e, h);
  //println("Forearm length: "+lowerArm.mag());

  //normalizes lower arm
  lowerArm.normalize();

  float currMag = (s.z)*savedUMag;
  currMag = currMag/savedZ;
  lowerArm.mult(-1*currMag*(2.0f/2.5f));

  //Adds an approximation of lower arm to elbow, which should therefore equal near where the wrist is
  wristPos= PVector.add(e, lowerArm);

  return wristPos;
}

public void sendJointPosition(int userId)
{
  PVector jointPos = new PVector();   // create a PVector to hold joint positions
 
 //Arms are reversed/mirrored. 
 
  // get the joint position of the left hand
  context.getJointPositionSkeleton(userId,SimpleOpenNI.SKEL_LEFT_HAND,jointPos);
 
 //Left arm opened/closed
 //0 is closed, 1 is open
 int closedLeft=0;
 if(leftHandOpen){
   closedLeft=1;
 }

   // create an osc message
  OscMessage leftarmMessage = new OscMessage("/leftarm");
 
 // send joint position of y axis by OSC
  leftarmMessage.add(jointPos.x);
  leftarmMessage.add(jointPos.y); 
  leftarmMessage.add(jointPos.z);
  leftarmMessage.add(closedLeft);
  
 
  // get the joint position of the right hand
  context.getJointPositionSkeleton(userId,SimpleOpenNI.SKEL_RIGHT_HAND,jointPos);
  
  
   // create an osc message
  OscMessage rightarmMessage = new OscMessage("/rightarm");
 
 //Right arm opened/closed
 //0 is closed, 1 is open
 int closedRight=0;
 
 if(rightHandOpen){
   //println("Right hand opened");
   closedRight=1;
   
 }
 
 // send joint position of y axis by OSC
  rightarmMessage.add(jointPos.x);
  rightarmMessage.add(jointPos.y); 
  rightarmMessage.add(jointPos.z);
  rightarmMessage.add(closedRight);
  
  // get the joint position of the right hand
  context.getJointPositionSkeleton(userId,SimpleOpenNI.SKEL_HEAD,jointPos);
 
   // create an osc message
  OscMessage headMessage = new OscMessage("/head");
 
 //println("Head position z: "+jointPos.z);
 
 // send joint position of all axises by OSC
  headMessage.add(jointPos.x);
  headMessage.add(jointPos.y); 
  headMessage.add(jointPos.z);
  
 
 
  // send the messages
  oscP5.send(rightarmMessage, myRemoteLocation);  
  oscP5.send(leftarmMessage, myRemoteLocation); 
  oscP5.send(headMessage, myRemoteLocation); 
}


// -----------------------------------------------------------------
// SimpleOpenNI user events

// draws a circle at the position of the head
public void circleForAHead(int userId)
{
  // get 3D position of a joint
  PVector jointPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_HEAD, jointPos);

  head=jointPos;
  
  // convert real world point to projective space
  PVector jointPos_Proj = new PVector(); 
  context.convertRealWorldToProjective(jointPos, jointPos_Proj);

  // a 200 pixel diameter head
  float headsize = 200;

  // create a distance scalar related to the depth (z dimension)
  float distanceScalar = (525/jointPos_Proj.z);

  // set the fill colour to make the circle green
  fill(255, 0, 0); 

  // draw the circle at the position of the head with the head size scaled by the distance scalar
  ellipse(jointPos_Proj.x, jointPos_Proj.y, distanceScalar*headsize, distanceScalar*headsize);
}

// draws a circle at the position of the right hand
public void circleForRightHand(int userId)
{
 // get 3D position of a joint
  PVector jointPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_RIGHT_HAND, jointPos);

  //Set up testing vectors
  rightHand=jointPos;

  // convert real world point to projective space
  PVector jointPos_Proj = new PVector(); 
  context.convertRealWorldToProjective(jointPos, jointPos_Proj);
  
  //Set up testing vectors
  rightHand=jointPos;

  PVector elbowPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_RIGHT_ELBOW, elbowPos);
  rightElbow=elbowPos;

  PVector shoulderPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_RIGHT_SHOULDER, shoulderPos);
  rightShoulder=shoulderPos;

  PVector hipPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_RIGHT_HIP, hipPos);
  rightHip=hipPos;
  
  // a 50 pixel diameter hand
  float handsize = 50;

  // create a distance scalar related to the depth (z dimension)
  float distanceScalar = (525/jointPos_Proj.z);

  // set the fill colour to make the circle red

  fill(255, 0, 0); 

  // draw the circle at the position of the head with the hand size scaled by the distance scalar
  ellipse(jointPos_Proj.x, jointPos_Proj.y, distanceScalar*handsize, distanceScalar*handsize);
}

// draws a circle at the position of the left hand
public void circleForLeftHand(int userId)
{
  // get 3D position of a joint
  PVector jointPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_LEFT_HAND, jointPos);

  //Set up testing vectors
  leftHand=jointPos;

  PVector elbowPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_LEFT_ELBOW, elbowPos);

  leftElbow=elbowPos;

  PVector shoulderPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_LEFT_SHOULDER, shoulderPos);

  leftShoulder=shoulderPos;

  PVector hipPos = new PVector();
  context.getJointPositionSkeleton(userId, SimpleOpenNI.SKEL_LEFT_HIP, hipPos);
  leftHip=hipPos;

  // convert real world point to projective space
  PVector jointPos_Proj = new PVector(); 
  context.convertRealWorldToProjective(jointPos, jointPos_Proj);


  // a 50 pixel diameter hand
  float handsize = 50;

  // create a distance scalar related to the depth (z dimension)
  float distanceScalar = (525/jointPos_Proj.z);

  // set the fill colour to make the circle green
  fill(0, 255, 0);

  // draw the circle at the position of the head with the hand size scaled by the distance scalar
  ellipse(jointPos_Proj.x, jointPos_Proj.y, distanceScalar*handsize, distanceScalar*handsize);
} 

// draw the skeleton with the selected joints
public void drawSkeleton(int userId)
{  
  // draw limbs  
  context.drawLimb(userId, SimpleOpenNI.SKEL_HEAD, SimpleOpenNI.SKEL_NECK);

  context.drawLimb(userId, SimpleOpenNI.SKEL_NECK, SimpleOpenNI.SKEL_LEFT_SHOULDER);
  context.drawLimb(userId, SimpleOpenNI.SKEL_LEFT_SHOULDER, SimpleOpenNI.SKEL_LEFT_ELBOW);
  context.drawLimb(userId, SimpleOpenNI.SKEL_LEFT_ELBOW, SimpleOpenNI.SKEL_LEFT_HAND);

  context.drawLimb(userId, SimpleOpenNI.SKEL_NECK, SimpleOpenNI.SKEL_RIGHT_SHOULDER);
  context.drawLimb(userId, SimpleOpenNI.SKEL_RIGHT_SHOULDER, SimpleOpenNI.SKEL_RIGHT_ELBOW);
  context.drawLimb(userId, SimpleOpenNI.SKEL_RIGHT_ELBOW, SimpleOpenNI.SKEL_RIGHT_HAND);

  context.drawLimb(userId, SimpleOpenNI.SKEL_LEFT_SHOULDER, SimpleOpenNI.SKEL_TORSO);
  context.drawLimb(userId, SimpleOpenNI.SKEL_RIGHT_SHOULDER, SimpleOpenNI.SKEL_TORSO);

  context.drawLimb(userId, SimpleOpenNI.SKEL_TORSO, SimpleOpenNI.SKEL_LEFT_HIP);
  context.drawLimb(userId, SimpleOpenNI.SKEL_LEFT_HIP, SimpleOpenNI.SKEL_LEFT_KNEE);
  context.drawLimb(userId, SimpleOpenNI.SKEL_LEFT_KNEE, SimpleOpenNI.SKEL_LEFT_FOOT);

  context.drawLimb(userId, SimpleOpenNI.SKEL_TORSO, SimpleOpenNI.SKEL_RIGHT_HIP);
  context.drawLimb(userId, SimpleOpenNI.SKEL_RIGHT_HIP, SimpleOpenNI.SKEL_RIGHT_KNEE);
  context.drawLimb(userId, SimpleOpenNI.SKEL_RIGHT_KNEE, SimpleOpenNI.SKEL_RIGHT_FOOT);
}

// Event-based Methods

// when a person ('user') enters the field of view
public void onNewUser(int userId)
{
  println("New User Detected - userId: " + userId);

  // start pose detection
  context.startPoseDetection("Psi", userId);
}

// when a person ('user') leaves the field of view 
public void onLostUser(int userId)
{
  println("User Lost - userId: " + userId);
}

// when a user begins a pose
public void onStartPose(String pose, int userId)
{
  println("Start of Pose Detected  - userId: " + userId + ", pose: " + pose);

  // stop pose detection
  context.stopPoseDetection(userId); 

  // start attempting to calibrate the skeleton
  context.requestCalibrationSkeleton(userId, true);
}

// when calibration begins
public void onStartCalibration(int userId)
{
  println("Beginning Calibration - userId: " + userId);
}

// when calibaration ends - successfully or unsucessfully 
public void onEndCalibration(int userId, boolean successfull)
{
  println("Calibration of userId: " + userId + ", successfull: " + successfull);

  if (successfull) 
  { 
    println("  User calibrated !!!");

    // begin skeleton tracking
    context.startTrackingSkeleton(userId);
    currUser=userId;
  } 
  else 
  { 
    println("  Failed to calibrate user !!!");

    // Start pose detection
    context.startPoseDetection("Psi", userId);
  }
}


// -----------------------------------------------------------------
// Keyboard events

public void keyPressed()
{
  switch(key)
  {
  case ' ':
    context.setMirror(!context.mirror());
    break;
  }

  switch(keyCode)
  {
  case LEFT:
    rotY += 0.1f;
    break;
  case RIGHT:
    // zoom out
    rotY -= 0.1f;
    break;
  case UP:
    if (keyEvent.isShiftDown())
      zoomF += 0.01f;
    else
      rotX += 0.1f;
    break;
  case DOWN:
    if (keyEvent.isShiftDown())
    {
      zoomF -= 0.01f;
      if (zoomF < 0.01f)
        zoomF = 0.01f;
    }
    else
      rotX -= 0.1f;
    break;
  }
}
class BoundingBox{
  //The numbers denote the points the line go between
  Line line12, line23, line34, line41;
  Line[] lines;
  
  /**
  Constructor for the BoundingBox, takes four points, creates
  four lines to make up the bounding box from these points
  
  */
  BoundingBox(PVector one, PVector two, PVector three, PVector four){
    line12= new Line(one.x,one.y,two.x,two.y);
    line23 = new Line(two.x,two.y,three.x,three.y);
    line34=new Line(three.x,three.y,four.x,four.y);
    line41=new Line(four.x,four.y,one.x,one.y);
    
    //println("line12: "+line12.toString());  
    //println("line23: "+line23.toString()); 
    //println("line34: "+line34.toString());   
    //println("line41: "+line41.toString());
    
    //Sets up array
    lines = new Line[4];
    lines[0]=line12;
    lines[1]=line23;
    lines[2]=line34;
    lines[3]=line41;
  }
  
  
  public void drawBox(){
    stroke(255,255,255);
    strokeWeight(10);
    for(int i =0; i<4; i++){
      line(lines[i].x1, lines[i].y1, lines[i].x2,lines[i].y2);
    }
  }
  
  //Checks if point is within, based on how many times it intersects
  //a line of the bounding box. Even=not within, odd=within
  public boolean contains(float a1, float b1, float a2, float b2){
     boolean within=false;
     int intersections=0;
     for(int i = 0; i<4; i++){
       if(lines[i].intersect(a1, b1, a2, b2)){
         intersections++;
       }
     }
     
     
     if(intersections!=0){
       if(intersections%2!=0){
         within=true;
       }
     }
     return within;
  }
  
  
  
  public float getMinX(){
    float mini = line12.x1;
    
    for(int i =0; i<4; i++){
      if(lines[i].x1<mini){
        mini=lines[i].x1;
      }
      if(lines[i].x2<mini){
        mini=lines[i].x2;
      }
    }
    
    return mini;
  }
  
  public float getMinY(){
    float mini = line12.y1;
    
    for(int i =0; i<4; i++){
      if(lines[i].y1<mini){
        mini=lines[i].y1;
      }
      if(lines[i].y2<mini){
        mini=lines[i].y2;
      }
    }
    
    return mini;
  }
  
  public float getMaxX(){
    float maxi = line12.x1;
    
    for(int i =0; i<4; i++){
      if(lines[i].x1>maxi){
        maxi=lines[i].x1;
      }
      if(lines[i].x2>maxi){
        maxi=lines[i].x2;
      }
    }
    
    return maxi;
  }
  
  public float getMaxY(){
    float maxi = line12.y1;
    
    for(int i =0; i<4; i++){
      if(lines[i].y1>maxi){
        maxi=lines[i].y1;
      }
      if(lines[i].y2>maxi){
        maxi=lines[i].y2;
      }
    }
    
    return maxi;
  }
}
/*
 * Finger detection class.
 * (c) Antonio Molinaro 2011 
 * http://code.google.com/p/blobscanner/.
 */

class FingerDetector {
  BoundingBox bBox;
  public PVector hand, wrist, a, b,c,d, pinky,thumb, prevThumb, prevPinky; 
  public PVector[] prevFingers;
  public PVector foreFinger, midFinger, ringFinger, pinkyFinger;
  
  float w, h, handLength;
  PImage out;
  
  FingerDetector(PImage _out, PVector _hand, PVector _wrist){
    out=_out;
    w=out.width;
    h=out.height;
    
    hand=_hand;
    wrist=_wrist;
    
    if( hand!=null && wrist !=null){
      //Extend palm up by 1/4 of the dist from wrist to palm
      //(Brings it right up to the knuckle level, instead of the plam)
      PVector handVec = PVector.sub(hand, wrist);
      handVec.mult(1.0f/4.0f);
      hand.add(handVec);
      
      removePalm();
    }
  }
  
  FingerDetector(PImage _out, PVector _hand, PVector _wrist, PVector _prevThumb, PVector _prevPinky, PVector[] _prevFingers){
    this(_out,_hand,_wrist);
    
    prevThumb =_prevThumb;
    prevPinky=_prevPinky;
    prevFingers=_prevFingers;
  }
  
  
  //Returns true if possible Thumb is not too close to hand, 
  //false otherwise
  public boolean inThumbRange(PVector possibleThumb){
    if(hand!=null && possibleThumb!=null && wrist!=null){
      PVector wristToThumb = PVector.sub(possibleThumb, wrist);
      PVector handToThumb = PVector.sub(possibleThumb, hand);
      
      if(handToThumb.mag()>handLength/4 && handToThumb.mag()<handLength*1.5f){
        if(wristToThumb.mag()>handLength/4 && wristToThumb.mag()<handLength*1.5f){
          return true;
        }
      }
    }
    
    
    return false;
  }
  
  public void removePalm(){
    PVector handPar = PVector.sub(hand, wrist);
    handPar.normalize();
    
    PVector wristPerp = new PVector();
    PVector wristPerp4 = new PVector();
    
    float perpX = handPar.x;
    float perpY = handPar.y;
    
    wristPerp.x = -1*perpY;
    wristPerp.y = perpX;
    
    wristPerp4.x= perpY;
    wristPerp4.y=-1*perpX;
    
    //Checks that they aren't of length 0
    //TRYING: REMOVING THIS
    //if(wristPerp.mag()>0){
      //Vector One of the bounding box
      wristPerp = wristExtensions(wristPerp);
      //Vector Four of the bounding box
      wristPerp4 = wristExtensions(wristPerp4);
     
      PVector hand2 = new PVector();
      hand2.x=wristPerp.x;
      hand2.y=wristPerp.y;
      
      hand2.sub(wrist);
      hand2.add(hand);
      
      PVector hand3 = new PVector();
      hand3.x=wristPerp4.x;
      hand3.y=wristPerp4.y;
      
      hand3.sub(wrist);
      hand3.add(hand);
      
      PVector handLength = PVector.sub(hand,wrist);
      handLength.mult(-1);
      handLength.add(wrist);
      
      wristPerp.sub(wrist);
      wristPerp4.sub(wrist);
      
      //Put on bit behind the wrist
      wristPerp.add(handLength);
      wristPerp4.add(handLength);
      
      a=wristPerp;
      b=hand2;
      c =wristPerp4;
      d=hand3;
      
      
      bBox = new BoundingBox(wristPerp, hand2, hand3, wristPerp4);
      if(wristPerp.x>0 || wristPerp.y>0 || wristPerp.x<w || wristPerp.y<h){
        blackenPalm();
      } 
      
    //}  
  }
  
  //Presupposes non-null thumb
  public boolean openHand(){
   
    PVector thumbLength = PVector.sub(thumb, wrist);
    
    if(thumbLength.mag()<handLength){
      return false;
    }
    else{
      return true;
    }
  }
  
  public PVector getPinky(){
    return pinky;
  }
  
  public PVector thumbDetection(){
    PVector thumb1 = null;
    PVector thumb2 = null;
    
    float length1 = 0.0f; 
    float length2= 0.0f;

    
    if(a!=null && b!=null && c!=null && d!=null){
     // println("To");
      if(a.x>0 && b.x>0 && a.y>0 && b.x>0){
      //  println("Figure");
        thumb1 = thumbPotentials(a,b);
        if(thumb1!=null){
          length1 = thumb1.z;
          thumb1.z=0.0f;
        }
        
      }
      if(c.x>0 && d.x>0 && c.y>0 && d.y>0){
        //println("This");
        thumb2 = thumbPotentials(c,d);
        if(thumb2!=null){
          length2 = thumb2.z;
          thumb2.z = 0.0f;
        }
      }
    }
    
    if(thumb1!=null && thumb2!=null){
      if(inThumbRange(thumb1) && inThumbRange(thumb2)){
        if(length1>length2){
          thumb=thumb1;
          pinky=thumb2;
          return thumb1;
        }
        else{
          pinky=thumb1;
          thumb=thumb2;
          return thumb2;
        }
      }
      else if(inThumbRange(thumb1)){
        thumb=thumb1;
        return thumb1;
      }
      else if(inThumbRange(thumb2)){
        thumb=thumb2;
        return thumb2;
      }
    }
    else if(thumb1!=null){
      if(inThumbRange(thumb1)){
        return thumb1;
      }
    }
    else if(thumb2!=null){
      if(inThumbRange(thumb2)){
        return thumb2;
      }
    }
    
    if(prevThumb!=null){
      PVector thumb3 =closestWhiteTo(prevThumb);
      if(thumb3!=null){
        return thumb3;
      }
    }
    
    
    
    /**
    if(thumb1!=null && thumb2!=null){
      
      if(length1>handLength/5 && length1<handLength && length2>handLength/5 && length2<handLength){
        if(length1>length2){
          pinky=thumb2;
          thumb = thumb1;
          return thumb1;
        }
        else{
          pinky = thumb1;
          thumb = thumb2;
          return thumb2;
        }
      }
      else if(length1>handLength/5 && length1<handLength){
        thumb = thumb1;
        return thumb1;
      }
      else if(length2>handLength/5 && length2<handLength){
        thumb = thumb2;
        return thumb2;
      }
    }
    else if(thumb1!=null && thumb2==null){
      if(length1>handLength/5 && length1<handLength){
        thumb = thumb1;
        return thumb1;
      }
    }
    else if(thumb2!=null && thumb1==null){
      if(length2>handLength/5 && length2<handLength){
        //println("Possibility 6");
        thumb = thumb2;
        return thumb2;
      }
    }
    
    if(prevThumb!=null){
      thumb =closestWhiteTo(prevThumb);
     
      //if(prevPinky!=null){
      //  pinky = closestWhiteTo(prevPinky);
      //}
      if(thumb!=null){
        //GETTING RID OF THIS TO SEE HOW LONG THE MAX leftThumbNullCounter SHOULD BE
        //println("It was the prev Thumb check! Thumb was: "+thumb);
      }
      
      return thumb;
    }
    */
    return null;
  }
  
  /**
  * Finds closest white pixel to previous, within a measure
  *
  */
 public PVector closestWhiteTo(PVector prev){
      //TESTING: 20 again, since I'm working on projective space with the thumb
      int maxDist = 20;
      int index = (int)(prev.x+prev.y*w);
      if(index>0 && index<out.pixels.length){
        if(brightness(out.pixels[index])==255){
          return prev;
        }
      }
      
      for(int x=(-1*maxDist+(int)prev.x); x<=(maxDist+(int)prev.x); x++){
        for(int y=(-1*maxDist+(int)prev.y); y<=(maxDist+(int)prev.y); y++){
          index= (int)(x+y*w);
          
          if(index>0 && index<out.pixels.length){
            //print("This is gonna suck, brightness: "+brightness(out.pixels[index]));
            if(brightness(out.pixels[index])==255){
              return new PVector(x,y);
            }
          }
        }
      }
      
      return null;
  }
  
  //Startpos will be the one closest to the wrist, endPos closest to the hand
  public PVector thumbPotentials(PVector startPos, PVector endPos){
    
    boolean onlyOne = false;
    boolean hitWhite = false;
    
    PVector startIncrement = PVector.sub(startPos, wrist);
    PVector endIncrement = PVector.sub(endPos, hand);
    
    PVector difference = PVector.sub(endPos, startPos);
    float handLength = difference.mag();
    
    this.handLength = handLength;
    
    PVector distToHand = new PVector(endIncrement.x, endIncrement.y);
    
    startIncrement.normalize();
    endIncrement.normalize();
    
    PVector thumbPos = null;
    
    float distExtended = 0.0f;
    
    //Has the max search value as handLength*2, which strikes me as far too much
    //TRYING: just handLength, I think I replaced that for a reason though, not sure
    while(!onlyOne && distToHand.mag() <(handLength*1.5f)){
      
      startPos.add(startIncrement);
      endPos.add(endIncrement);
      distExtended++;
      
      distToHand = PVector.sub(endPos, hand);
      
      PVector diff = PVector.sub(endPos,startPos);
      float distance = diff.mag();
      diff.normalize();
      PVector movePnt = new PVector(startPos.x,startPos.y);
      
      int whiteCnt = 0;
      float savedX = 0.0f;
      float savedY = 0.0f;
      
      while(PVector.sub(movePnt, startPos).mag() <distance){
        
        movePnt.add(diff);
        
        int index = (int)movePnt.x+(int)movePnt.y*(int)w;
        if(index>=0 &&out.pixels.length>index){
          if(brightness(out.pixels[index])==255 && red(out.pixels[index])==255){
            whiteCnt++;
            //TRYING TO DO THIS
            //if(thumbChecker(new PVector(movePnt.x,movePnt.y))){
              savedX = movePnt.x;
              savedY = movePnt.y;
            //}
          }
        }
      }
      if(whiteCnt>0){
        //If you haven't hit white yet
        if(!hitWhite){
          hitWhite=true;
        }
        //If you have hit white already, and there's only one you have a thumb
        else{
          if(whiteCnt==1){
            onlyOne=true;
            thumbPos = new PVector(savedX, savedY, distExtended);
          }
        }
      }
    }
    
    
   return thumbPos;
  }
  
  public PImage getImage(){
    return out;
  }
  
  public PVector wristExtensions(PVector wristVec){
   
    PVector orig = new PVector();
    orig.x=wristVec.x;
    orig.y=wristVec.y;
    
    wristVec.add(wrist);
    
    boolean onWrist = true;
    while(onWrist){
      wristVec.add(orig);
      
     int index = (int)wristVec.x+(int)wristVec.y*(int)w;
      if(index>0 && index< out.pixels.length){
        if(brightness(out.pixels[index])==0){
          onWrist=false;
        }
        
      }
      else{
        onWrist=false;
      }
    }
    
    return wristVec;
  }
  
  
  //Just code here in case I decide I need it for later
  //Prolly actually use thumb and handPos (for middle). With those you 
  //could determine what each one is. 
  //For now, just uses thumb
  public void assignFingers(){
    PVector[] fingers = pickOutFingers();
    int fingerCnt = 0;
    for(PVector finger: fingers){
      if(finger!=null){
        fingerCnt++;
        
      }
    }
    
    if(fingerCnt==4 && thumb!=null){
      //We already know they're all nonNull
      int placedCnt = 0;
      while(placedCnt<4){
        float distance =1000.0f;
        PVector minimum=null;
        for(PVector finger: fingers){
          if(finger!=null){
            PVector fingDiff = PVector.sub(finger, thumb);
            if(fingDiff.mag()<distance){
              minimum=fingDiff;
              distance=fingDiff.mag();
            }
            
        
          }
        }
        
        if(minimum!=null){
          if(placedCnt==0){
            foreFinger=minimum;
          }
          else if(placedCnt==1){
            midFinger=minimum;
           
          }
          else if(placedCnt==2){
           ringFinger=minimum; 
          }
          else if(placedCnt==3){
            pinkyFinger=minimum;
          }
          placedCnt++;
        }
        else{
          //You didn't have a minimum this time, go ahead and finish.
          placedCnt=5;
        }
        
      }
    }
  }
  
  public PVector[] pickOutFingers(){
    //b and d are closest to the hand
   
    PVector[] fingers = new PVector[4];
    int pointer = 0; 
    
    if(b==null || d==null){
      return fingers;
    }
    
    PVector startPos = PVector.sub(b,hand);
    PVector endPos = PVector.sub(d,hand);
     
    //Make it wider
    startPos.mult(2);
    endPos.mult(2);
    
    startPos.add(hand);
    endPos.add(hand);
    PVector increment = PVector.sub(hand,wrist);
    increment.normalize();
    
    float difference = 0.0f;
    
    float savedX=0.0f;
    float savedY=0.0f;
    while(difference<handLength*2 && pointer<fingers.length){
      startPos.add(increment);
      endPos.add(increment);
      difference++;
      
      PVector movePnt = new PVector(startPos.x,startPos.y);
      
      PVector diff = PVector.sub(endPos, startPos);
      float distance = diff.mag();
      diff.normalize();
      
      boolean white=false;
      boolean black=false;
      int whiteCnt = 0;
      
      while(PVector.sub(movePnt, startPos).mag()<distance && pointer<fingers.length){
        movePnt.add(diff);
        
        
        int index = (int)movePnt.x+(int)movePnt.y*(int)w;
        if(index>=0 &&out.pixels.length>index){
          if(brightness(out.pixels[index])==255 && red(out.pixels[index])==255){
            
            //If previously black
            if(black){
              black = false;
            }
            //If previously white
            if(white){
              whiteCnt++;
              savedX = movePnt.x;
              savedY = movePnt.y;
            }            
            white=true;
          }
          else if(brightness(out.pixels[index])==0){
            
            //If previously white
            if(white){
              //If white count is less than 3, you found a finger!
              if(whiteCnt<3 && whiteCnt>0){
                //If not a duplicate
                if(!duplicate(fingers,new PVector(savedX,savedY))){
                  fingers[pointer] = new PVector(savedX,savedY);
                  pointer++;
                }
              }
              
              white=false;
            }
            black=true;
            
            
          }
        }
      }
      
    }
    
    return fingers;
  }
  
  //Assumes finger is non-null
  public boolean duplicate(PVector[] fingers, PVector finger){
    //Might want to make this measure based on handLength or sumting
    int measure = 10;
    boolean duplicate = false;
    for(int i = 0; i<fingers.length; i++){
      if(fingers[i] !=null){
        if(fingers[i].x+measure>finger.x && fingers[i].x-measure<finger.x){
          if(fingers[i].y+measure>finger.y && fingers[i].y-measure<finger.y){
            duplicate=true;
          }
        }
      }
    }
    if(thumb!=null){
      if(thumb.x+measure>finger.x && thumb.x-measure<finger.x){
        if(thumb.y+measure>finger.y && thumb.y-measure<finger.y){
          duplicate=true;
        }
      }
    }
    
    if(pinky!=null){
      if(pinky.x+measure>finger.x && pinky.x-measure<finger.x){
        if(pinky.y+measure>finger.y && thumb.y-measure<finger.y){
          duplicate=true;
        }
      }
    }
    
    
    return duplicate;
  }
  
  /**
  * Determines if a past in Vector could be a thumb based on how quickly it hits black pixels on either side
  *
  */
  public boolean thumbChecker(PVector possibleThumb){
    if(hand!=null && possibleThumb!=null){
      if(thumbHeightChecker(possibleThumb) && thumbWidthChecker(possibleThumb)){
        return true;
      }
      else{
        return false;
      }
    }
    else{
      return false;
    }
  }
  
  /**
  * Based on formula found here: 
  * http://local.wasp.uwa.edu.au/~pbourke/geometry/pointline/
  *
  * P3: possibleThumb
  * P2: hand
  * P1: wrist
  */
  public boolean thumbHeightChecker(PVector possibleThumb){
    
    float u = (possibleThumb.x-wrist.x)*(hand.x-wrist.x)+(possibleThumb.y-wrist.y)*(hand.y-wrist.y);
    
    u=u/(handLength*handLength);
    
    
    float closestXOnLine = wrist.x+u*(hand.x-wrist.x);
    float closestYOnLine = wrist.y+u*(hand.y=wrist.y);
    
    PVector closestPnt = new PVector(closestXOnLine,closestYOnLine);
    
    float thumbHeight = PVector.sub(possibleThumb,closestPnt).mag();
    
    if(thumbHeight>(handLength/3.0f) && thumbHeight<handLength*1.5f){
      return true;
    }
    else{
      return false;
    }
  }
  
  //Checks if it's the right width
  public boolean thumbWidthChecker(PVector possibleThumb){
     float thumbWidth = handLength/4;
    PVector thumbWrist = PVector.sub(possibleThumb,wrist);
    
    PVector leftPerp = new PVector(thumbWrist.y,-1*thumbWrist.x);
    PVector rightPerp = new PVector(-1*thumbWrist.y, thumbWrist.x);
    
    boolean left=false;
    boolean right=false;
    
    leftPerp.normalize();
    rightPerp.normalize();
    
    PVector leftIncrement = new PVector(leftPerp.x, leftPerp.y);
    PVector rightIncrement = new PVector(rightPerp.x, rightPerp.y);
    
    leftPerp.add(possibleThumb);
    rightPerp.add(possibleThumb);
    
    PVector leftCheck = PVector.sub(leftPerp, possibleThumb);
    
    
    while(leftCheck.mag()<thumbWidth){
      int index = (int)(leftPerp.x+leftPerp.y*w);
      if(index>0 && index<out.pixels.length){
        if(brightness(out.pixels[index])==0){
          left=true;
        }
      }
      
      leftPerp.add(leftIncrement);
      leftCheck = PVector.sub(leftPerp, possibleThumb);
    }
    
    PVector rightCheck = PVector.sub(rightPerp, possibleThumb);
    
    while(rightCheck.mag()<thumbWidth){
      int index = (int)(rightPerp.x+rightPerp.y*w);
      if(index>0 && index<out.pixels.length){
        if(brightness(out.pixels[index])==0){
          right=true;
        }
      }
      
      rightPerp.add(rightIncrement);
      rightCheck = PVector.sub(rightPerp, possibleThumb);
    }
    
    //Has to be both to be tue
    boolean answer = left && right;
    
    return answer;
  }
  
  /**
  * Figures out if pixel is in bounding box, if so, sets to black. 
  *
  *
  */
  public void blackenPalm(){
     for(int y=0; y<out.height; y++){
      for(int x=0; x<out.height; x++){
        int index = (int)x+(int)y*out.width;
        
        if(index>0 && index<out.pixels.length){
          //Checks if in Palm box, sets to black if so
          if(bBox.contains(0.0f,0.0f,x,y)){
            out.pixels[index]=color(0,0,0);
            
          }
          //Get rid of the arm too, why not?
          else if(green(out.pixels[index])==255 && red(out.pixels[index])==0){
            out.pixels[index]=color(0,0,0);
          }
          
        }
        
        
      }
    }
  }
}

class GestureController{
  //Right Stuff (And head)
  PVector head, rHand, rWrist, rElbow, rShoulder, rHip;
  //Left Stuff
  PVector lHand, lWrist, lElbow, lShoulder, lHip;
  PVector prevLeftHand= null;
  PVector prevRightHand=null;
  float leftHandMvmt = 0.0f;
  float rightHandMvmt = 0.0f;
  
  final int DETECT_LEFTARM_STRAIGHT=0;
  final int DETECT_LEFTARM_BENT=1;
  final int DETECT_LEFTARM_FRONT=2;
  final int DETECT_LEFTHAND_IN=3;
  final int DETECT_LEFTHAND_OUT=4;
  final int DETECT_LEFTHAND_DOWN=5;
  //Above shoulder
  final int DETECT_LEFTHAND_UP=6;
  final int DETECT_LEFTHAND_AT_MOUTH=7;
  
  final int DETECT_RIGHTARM_STRAIGHT=8;
  final int DETECT_RIGHTARM_BENT=9;
  final int DETECT_RIGHTARM_FRONT=10;
  final int DETECT_RIGHTHAND_IN=11;
  final int DETECT_RIGHTHAND_OUT=12;
  final int DETECT_RIGHTHAND_DOWN=13;
  //About shoulder
  final int DETECT_RIGHTHAND_UP=14;
  final int DETECT_RIGHTHAND_AT_MOUTH=15;
  
  final int DETECT_HANDS_TOGETHER=16;
  
  int complexGestureStart=17;
  
  //Complex gestures
  final int DETECT_SWORD_SWING=17;
  final int DETECT_HOLDING_REINS=18;
  final int DETECT_BOW_N_ARROW=19;
  final int DETECT_AIR_GUITAR=20;
  final int DETECT_HARP=21;
  final int DETECT_CROCODILE_ARMS=22;
  final int DETECT_FLYING=23;
  final int DETECT_WAND_TWIRL=24;
  final int DETECT_CASTING_MAGIC=25;
  final int DETECT_CRYSTAL_BALL=26;
  final int DETECT_PROTECT_TREASURE=27;
  final int DETECT_EAT_OR_DRINK=28;
  
  final int DETECT_INTERP_PUNCH_AT=29;
  final int DETECT_INTERP_SMOKE_PONDER=30;
  final int DETECT_INTERP_ARMS_CROSSED=31;
  final int DETECT_INTERP_CUFFED=32;
  final int DETECT_INTERP_TABLE_WIPER=33;
  final int DETECT_INTERP_LOW_DRAW=34;
  final int DETECT_INTERP_HIGH_DRAW=35;
  final int DETECT_INTERP_SWEEP=36;
  final int DETECT_INTERP_FIDDLE_WITH_SMALL_THING=37;
  final int DETECT_INTERP_STAND_LEGS=38;
  final int DETECT_INTERP_BOTH_HANDS_BY_CHEST=39;
  final int DETECT_INTERP_ARM_BY_BICEP=40;
  final int DETECT_INTERP_HANDS_TOGETHER=41;
  final int DETECT_INTERP_PLAY_CARD=42;
  final int DETECT_INTERP_ONE_HAND_UP=43;
  final int DETECT_MOVING_RUSH_AT=44;
  
  final int DETECT_SIGNAL_TYPES=45;
  
  public boolean[] detectSignals = new boolean[DETECT_SIGNAL_TYPES];
  public double[] confidence = new double[DETECT_SIGNAL_TYPES];
  private boolean leftXMvmt, leftYMvmt, leftZMvmt, rightXMvmt, rightYMvmt, rightZMvmt;  

  GestureController(PVector[] skeleton){
    head=skeleton[0];
    rHand = skeleton[1];
    rWrist=skeleton[2];
    rElbow=skeleton[3];
    rShoulder=skeleton[4];
    rHip=skeleton[5];
    lHand = skeleton[6];
    lWrist=skeleton[7];
    lElbow=skeleton[8];
    lShoulder=skeleton[9];
    lHip = skeleton[10];
  }  
  
  
  public String[] update(PVector[] skeleton){
    setSkeleton(skeleton);
    resetGestures();
    
    if(prevRightHand!=null && prevLeftHand!=null){
      simpleGestureChecker();
      complexGesturesChecker();
      chrisGesturesChecker();
    }
    
    prevRightHand=rHand;
    prevLeftHand=lHand;
    
    setConfidence();
    
    return completedGestures();
  }
  
  //Determines the facing, potentially for use with Chris' stuff (prolly will be)
  public void facingChecker(){
    float shoulderAngle = (float) atan2(rShoulder.z-lShoulder.z,rShoulder.x-lShoulder.x);
    
    if (shoulderAngle < -0.35f){
      println("Facing left");
    }
    else if (shoulderAngle > 0.35f){
      println("Facing right");
    }
    else{
      println("Facing Camera");
    }
  }
  
  public void setConfidence(){
    for(int i =0; i<confidence.length; i++){
      if(confidence[i]>0){
        confidence[i]-=0.1f;
      }
    }
    
    for(int i =0; i<detectSignals.length; i++){
      if(detectSignals[i]==true){
        confidence[i]=1.0f;
      }
    }
    
    
  }
  
  //Sets all to false
  public void resetGestures(){
    for(int i =0; i<detectSignals.length; i++){
      detectSignals[i]=false;
    }
    
    leftXMvmt=false;
    leftYMvmt=false;
    leftZMvmt=false;
    rightXMvmt=false;
    rightYMvmt=false;
    rightZMvmt=false;
  }
  
  /**
    Returns string of completed gestures
  */
  
  public String[] completedGestures(){
    String[] gestures = new String[DETECT_SIGNAL_TYPES];
    
    for(int i =0; i<DETECT_SIGNAL_TYPES; i++){
      if(detectSignals[i]|| confidence[i]>0.6f){
        String gestureName = "";
        
        switch(i){
          /*
          case DETECT_LEFTARM_STRAIGHT: gestureName= "Left Arm Straight";
            break;
          case DETECT_LEFTARM_BENT: gestureName="Left Arm Bent";
            break;
          case DETECT_LEFTARM_FRONT: gestureName="Left Arm Front";
            break;
          case DETECT_LEFTHAND_IN: gestureName = "Left Hand In";
            break;
          case DETECT_LEFTHAND_OUT: gestureName = "Left Hand Out";
            break;
          case DETECT_LEFTHAND_UP: gestureName = "Left Hand Up";
            break;
          case DETECT_LEFTHAND_AT_MOUTH: gestureName = "Left Hand At Mouth";
            break;
          case DETECT_RIGHTARM_STRAIGHT: gestureName= "Right Arm Straight";
            break;
          case DETECT_RIGHTARM_BENT: gestureName="Right Arm Bent";
            break;
          case DETECT_RIGHTARM_FRONT: gestureName="Right Arm Front";
            break;
          case DETECT_RIGHTHAND_IN: gestureName = "Right Hand In";
            break;
          case DETECT_RIGHTHAND_OUT: gestureName = "Right Hand Out";
            break;
          case DETECT_RIGHTHAND_UP: gestureName = "Right Hand Up";
            break;
          case DETECT_RIGHTHAND_AT_MOUTH: gestureName = "Right Hand At Mouth";
            break;
          case DETECT_HANDS_TOGETHER: gestureName = "Hands Together";
            break;
          */  
            
          case DETECT_SWORD_SWING: gestureName = "Sword Swing";
            break;
          case DETECT_HOLDING_REINS: gestureName="Holding Reins";
            break;
          case DETECT_BOW_N_ARROW: gestureName = "Bow n' Arrow";
            break;
          case DETECT_AIR_GUITAR: gestureName = "Air Guiar";
            break;
          case DETECT_HARP: gestureName = "Harp";
            break;
          case DETECT_CROCODILE_ARMS: gestureName = "Crocodile Arms";
            break;
          case DETECT_FLYING: gestureName = "Flying";
            break;
          case DETECT_WAND_TWIRL: gestureName = "Wand Twirl";
            break;
          case DETECT_CASTING_MAGIC: gestureName = "Casting Magic";
            break;
          case DETECT_CRYSTAL_BALL: gestureName = "Scrying";
            break;
          case DETECT_PROTECT_TREASURE: gestureName ="Protect Treasure";
            break;
          case DETECT_EAT_OR_DRINK: gestureName = "Eating or Drinking";
            break;
          
          case DETECT_INTERP_PUNCH_AT: gestureName = "Punching";
            break;
          case DETECT_INTERP_SMOKE_PONDER: gestureName= "Smoking/Pondering";
            break;
          case DETECT_INTERP_ARMS_CROSSED: gestureName= "Arms crossed";
            break;
          case DETECT_INTERP_CUFFED: gestureName = "Handcuffed";
            break;
          case DETECT_INTERP_TABLE_WIPER: gestureName = "Table Wiping";
            break;
          case DETECT_INTERP_LOW_DRAW: gestureName= "Low Draw";
            break;
          case DETECT_INTERP_HIGH_DRAW: gestureName= "High Draw";
            break;
          case DETECT_INTERP_SWEEP: gestureName = "Sweeping";
            break;
          case DETECT_INTERP_FIDDLE_WITH_SMALL_THING: gestureName ="Fiddling";
            break;
          case DETECT_INTERP_ARM_BY_BICEP: gestureName= "Hand by biceps";
            break;
          case DETECT_INTERP_STAND_LEGS: gestureName ="Standing";
            break;
          case DETECT_INTERP_BOTH_HANDS_BY_CHEST: gestureName = "Both hands by chest";
            break;
          case DETECT_INTERP_PLAY_CARD: gestureName = "Play Card";
            break;
          case DETECT_INTERP_ONE_HAND_UP: gestureName = "One hand up";
            break;
          case DETECT_MOVING_RUSH_AT: gestureName = "Moving rush at";
            break;
          //default: gestureName = "N/A";
          //  break;
        }
        gestures[i]=gestureName;
      }
      
    }
    return gestures;
  }
  
  
  /**
    Establishes all different gestural parts
    as either true or false
  */
  public void simpleGestureChecker(){
    PVector rUpperArm=PVector.sub(rShoulder, rElbow);
    PVector rLowerArm = PVector.sub(rHand, rElbow);
    
    PVector lUpperArm = PVector.sub(lShoulder, lElbow);
    PVector lLowerArm = PVector.sub(lHand, lElbow);
    
    float rAngle = PVector.angleBetween(rUpperArm, rLowerArm);
    float lAngle = PVector.angleBetween(lUpperArm, lLowerArm);
    
    rAngle = degrees(rAngle);
    lAngle = degrees(lAngle);
    
    //println("Right angle: "+rAngle);
    //println("Left angle: "+lAngle);
    
    if(lAngle>150){
      detectSignals[DETECT_LEFTARM_STRAIGHT]=true;
    }
    else if(lAngle<115){
      detectSignals[DETECT_LEFTARM_BENT]=true;
    }
    
    if(rAngle>150){
      detectSignals[DETECT_RIGHTARM_STRAIGHT]=true;
    }
    else if(rAngle<115){
      detectSignals[DETECT_RIGHTARM_BENT]=true;
    }
    
    //If right hand is more left than right shoulder
    if(rShoulder.x>rHand.x && rHand.x>lShoulder.x){
      detectSignals[DETECT_RIGHTHAND_IN]=true;
    }
    
    //If left hand is more right than left shoulder
    if(lShoulder.x<lHand.x && lHand.x<rShoulder.x){
      detectSignals[DETECT_LEFTHAND_IN]=true;
    }
    
    if(rShoulder.x<rElbow.x && rElbow.x<rHand.x){
      detectSignals[DETECT_RIGHTHAND_OUT]=true;
    }
    if(lShoulder.x>lElbow.x && lElbow.x>lHand.x){
      detectSignals[DETECT_LEFTHAND_OUT]=true;
    }
    
    if(lShoulder.y<lHand.y){
      detectSignals[DETECT_LEFTHAND_UP]=true;
    }
    
    if(rShoulder.y<rHand.y){
      detectSignals[DETECT_RIGHTHAND_UP]=true;
    }
    
    if(lHand.y<lElbow.y && lElbow.y<lShoulder.y){
      detectSignals[DETECT_LEFTHAND_DOWN]=true;
    }
    if(rHand.y<rElbow.y && rElbow.y<rShoulder.y){
      detectSignals[DETECT_RIGHTHAND_DOWN]=true;
    }
    
    float xRDist = abs(head.x-rHand.x);
    float yRDist = abs(head.y-rHand.y);
    
    float xLDist = abs(head.x-lHand.x);
    float yLDist = abs(head.y-lHand.y);
    
    if(xRDist<100 && yRDist<200){
      detectSignals[DETECT_RIGHTHAND_AT_MOUTH]=true;
    }
    
    if(xLDist<100 && yLDist<200){
      detectSignals[DETECT_LEFTHAND_AT_MOUTH]=true;
    }
    
    float xHandDist = abs(rHand.x-lHand.x);
    float yHandDist = abs(rHand.y-lHand.y);
    
    //println("X Hand Dist: "+xHandDist);
    //println("Y Hand Dist: "+yHandDist);
    
    if(xHandDist< 140 && yHandDist <140){
      detectSignals[DETECT_HANDS_TOGETHER]=true;
    }
    
    if(lHand.z<lElbow.z && lElbow.z<lShoulder.z){
      detectSignals[DETECT_LEFTARM_FRONT]=true;
    }
    
    if(rHand.z<rElbow.z && rElbow.z<rShoulder.z){
      detectSignals[DETECT_RIGHTARM_FRONT]=true;
    }
    
    PVector rHandDiff = PVector.sub(rHand, prevRightHand);
    PVector lHandDiff = PVector.sub(lHand, prevLeftHand);
    
    PVector rDiffNorm = new PVector (rHandDiff.x,rHandDiff.y,rHandDiff.z);
    PVector lDiffNorm = new PVector (lHandDiff.x,lHandDiff.y,lHandDiff.z);
    
    rDiffNorm.normalize();
    lDiffNorm.normalize();
    
    //Mostly in x direction
    if(abs(lDiffNorm.x)>0.8f){
      leftXMvmt=true;
    }
    //Mostly in y direction
    else if(abs(lDiffNorm.y)>0.8f){
      leftYMvmt=true;
    }
    //Mostly in z direction
    else if(abs(lDiffNorm.z)>0.8f){
      leftZMvmt=true;
    }
    
    //Mostly in x direction
    if(abs(rDiffNorm.x)>0.8f){
      rightXMvmt=true;
    }
    else if(abs(rDiffNorm.y)>0.8f){
      rightYMvmt=true;
    }
    else if(abs(rDiffNorm.z)>0.8f){
      rightZMvmt=true;
    }
    
    rightHandMvmt = rHandDiff.mag();
    leftHandMvmt = lHandDiff.mag();
  }
  
  /**
    Establishes the complex gestures
    (Gestures made up of various simple gestures)
    as either true or false.
  */
  public void complexGesturesChecker(){
    
    PVector leftHandToElbow = PVector.sub(lHand,rElbow);
    PVector rightHandToElbow = PVector.sub(rHand,lElbow);
    
    boolean leftNearRightElbow = leftHandToElbow.mag()<250.0f;
    boolean rightNearLeftElbow = rightHandToElbow.mag()<250.0f;
    
    float handMvmtDiff = abs(rightHandMvmt-leftHandMvmt);
    float notMuchMvmt=32.0f;
    
    boolean handsClose = PVector.sub(lHand,rHand).mag()<200.0f;
    
    detectSignals[DETECT_SWORD_SWING]= ((detectSignals[DETECT_LEFTARM_FRONT] && leftHandMvmt>65.0f && rightHandMvmt<notMuchMvmt && !detectSignals[DETECT_RIGHTHAND_UP])
    || (detectSignals[DETECT_RIGHTARM_FRONT] && rightHandMvmt>65.0f && leftHandMvmt<notMuchMvmt && !detectSignals[DETECT_LEFTHAND_UP])) && !detectSignals[DETECT_HANDS_TOGETHER];
    
    detectSignals[DETECT_HOLDING_REINS]= (detectSignals[DETECT_HANDS_TOGETHER] && detectSignals[DETECT_LEFTHAND_IN] && detectSignals[DETECT_RIGHTHAND_IN] &&
    rightHandMvmt>0 && handMvmtDiff<notMuchMvmt && rightYMvmt && leftYMvmt);    
    
    /*
    println("Left hand movement: "+leftHandMvmt);
    println("Right hand movement: "+rightHandMvmt);
    println("Right Y Movement: "+rightYMvmt);
    println("Left Y Movement: "+leftYMvmt);
    */
    
    //Works only on the side, never facing straight forward
    detectSignals[DETECT_BOW_N_ARROW]= 
    ((detectSignals[DETECT_LEFTARM_FRONT] && detectSignals[DETECT_LEFTHAND_OUT] && detectSignals[DETECT_LEFTARM_STRAIGHT] && (detectSignals[DETECT_RIGHTARM_BENT] || detectSignals[DETECT_RIGHTHAND_IN]) && !detectSignals[DETECT_RIGHTHAND_DOWN] 
    && !detectSignals[DETECT_LEFTHAND_DOWN] && rightHandMvmt>30.0f && leftHandMvmt<2*notMuchMvmt && !handsClose && !leftNearRightElbow && !rightNearLeftElbow) 
    || 
    (detectSignals[DETECT_RIGHTARM_FRONT] && detectSignals[DETECT_RIGHTHAND_OUT] && detectSignals[DETECT_RIGHTARM_STRAIGHT] && (detectSignals[DETECT_LEFTARM_BENT] || detectSignals[DETECT_LEFTHAND_IN]) && !detectSignals[DETECT_LEFTHAND_DOWN] 
    && !detectSignals[DETECT_RIGHTHAND_DOWN] && leftHandMvmt>30.0f && rightHandMvmt<2*notMuchMvmt) && !handsClose && !leftNearRightElbow && !rightNearLeftElbow);
    //||
    //((detectSignals[DETECT_HANDS_TOGETHER] && leftZMvmt)
    //||
    //(detectSignals[DETECT_HANDS_TOGETHER] &&rightZMvmt));
    
    detectSignals[DETECT_AIR_GUITAR]= (detectSignals[DETECT_LEFTARM_BENT] && detectSignals[DETECT_LEFTHAND_IN] && !detectSignals[DETECT_RIGHTHAND_IN] && detectSignals[DETECT_RIGHTHAND_UP] && detectSignals[DETECT_RIGHTHAND_OUT])
    || (detectSignals[DETECT_RIGHTARM_BENT] && detectSignals[DETECT_RIGHTHAND_IN] && !detectSignals[DETECT_LEFTHAND_IN] && detectSignals[DETECT_LEFTHAND_UP] && detectSignals[DETECT_LEFTHAND_OUT]);
    
    //Both on one side, that side arm bent
    detectSignals[DETECT_HARP]= (detectSignals[DETECT_LEFTHAND_OUT] && detectSignals[DETECT_LEFTHAND_UP] && !detectSignals[DETECT_RIGHTHAND_IN] && rHand.x<lShoulder.x && rHand.x>lHand.x)
    || (detectSignals[DETECT_RIGHTHAND_OUT] && detectSignals[DETECT_RIGHTHAND_UP] && !detectSignals[DETECT_LEFTHAND_IN] && lHand.x>rShoulder.x && lHand.x<rHand.x);
    
    //All sorts of perfect
    float handXDiff = lHand.x-rHand.x;
    
    detectSignals[DETECT_CROCODILE_ARMS]= (detectSignals[DETECT_LEFTARM_FRONT] && detectSignals[DETECT_RIGHTARM_FRONT] && detectSignals[DETECT_LEFTHAND_IN] && 
    detectSignals[DETECT_RIGHTHAND_IN] && (leftYMvmt||rightYMvmt) && lHand.y>lHip.y && rHand.y>rHip.y && handXDiff<50.0f && !detectSignals[DETECT_HANDS_TOGETHER]);
    
    detectSignals[DETECT_FLYING]= (detectSignals[DETECT_LEFTARM_STRAIGHT] && detectSignals[DETECT_RIGHTARM_STRAIGHT] && detectSignals[DETECT_LEFTHAND_OUT] && detectSignals[DETECT_RIGHTHAND_OUT]
    && leftYMvmt && rightYMvmt);
    
    detectSignals[DETECT_WAND_TWIRL]= ((detectSignals[DETECT_LEFTARM_FRONT] && leftHandMvmt>notMuchMvmt && rightHandMvmt<notMuchMvmt/2 && !detectSignals[DETECT_HANDS_TOGETHER]
    && !leftNearRightElbow && !rightNearLeftElbow && !detectSignals[DETECT_LEFTHAND_DOWN] && detectSignals[DETECT_RIGHTHAND_DOWN])
    || 
    (detectSignals[DETECT_RIGHTARM_FRONT] && rightHandMvmt>notMuchMvmt && !detectSignals[DETECT_LEFTHAND_UP] && leftHandMvmt<notMuchMvmt/2 && !detectSignals[DETECT_HANDS_TOGETHER]
    && !leftNearRightElbow && !rightNearLeftElbow && !detectSignals[DETECT_LEFTHAND_DOWN] && detectSignals[DETECT_LEFTHAND_DOWN]));
    
    float yHandDiff = abs(rHand.y-lHand.y);
    
    detectSignals[DETECT_CASTING_MAGIC]= (detectSignals[DETECT_LEFTARM_FRONT] && detectSignals[DETECT_RIGHTARM_FRONT] && detectSignals[DETECT_LEFTARM_BENT] && detectSignals[DETECT_RIGHTARM_BENT]
    && !handsClose && yHandDiff<100);
    
    detectSignals[DETECT_CRYSTAL_BALL]= ((detectSignals[DETECT_LEFTARM_BENT] || detectSignals[DETECT_RIGHTARM_BENT]) && detectSignals[DETECT_LEFTHAND_IN] && detectSignals[DETECT_RIGHTHAND_IN] &&
    (yHandDiff>200));
        
    //Works fairly well, maybe use left/right ZMvmt
    detectSignals[DETECT_PROTECT_TREASURE]=(!detectSignals[DETECT_LEFTHAND_UP] && !detectSignals[DETECT_LEFTHAND_IN] && detectSignals[DETECT_RIGHTARM_FRONT] && detectSignals[DETECT_RIGHTHAND_IN] && leftHandMvmt<notMuchMvmt && rightHandMvmt>55 &&
    rightZMvmt ) ||
    (!detectSignals[DETECT_RIGHTHAND_UP] && !detectSignals[DETECT_RIGHTHAND_IN] && detectSignals[DETECT_LEFTARM_FRONT] && detectSignals[DETECT_LEFTHAND_IN] && rightHandMvmt<notMuchMvmt && leftHandMvmt>55 &&
    leftZMvmt);
    
    detectSignals[DETECT_EAT_OR_DRINK]= (detectSignals[DETECT_LEFTHAND_AT_MOUTH] && detectSignals[DETECT_RIGHTHAND_DOWN] && !detectSignals[DETECT_RIGHTHAND_IN]) ||
    (detectSignals[DETECT_RIGHTHAND_AT_MOUTH] && detectSignals[DETECT_LEFTHAND_DOWN] && ! detectSignals[DETECT_RIGHTHAND_IN]);
    
    }
  
  
  public void chrisGesturesChecker(){
    float waggleLeftHand = abs(lHand.x-prevLeftHand.x);
    waggleLeftHand*=0.9f;
    
    float waggleRightHand = abs(rHand.x-prevRightHand.x);
    waggleRightHand*=0.9f;
    
    //println("WaggleLeftHand: "+waggleLeftHand);
    //println("WaggleRightHand: "+waggleRightHand);
    
    detectSignals[DETECT_INTERP_PUNCH_AT]=((detectSignals[DETECT_LEFTHAND_OUT] && detectSignals[DETECT_RIGHTHAND_DOWN] && waggleLeftHand > 160.0f) ||
    (detectSignals[DETECT_LEFTHAND_DOWN] && detectSignals[DETECT_RIGHTHAND_OUT] && waggleRightHand > 160.0f)) && !detectSignals[DETECT_HANDS_TOGETHER];
    
    detectSignals[DETECT_INTERP_SMOKE_PONDER] = (detectSignals[DETECT_LEFTHAND_AT_MOUTH] && detectSignals[DETECT_RIGHTHAND_DOWN]) ||
    (detectSignals[DETECT_LEFTHAND_DOWN] && detectSignals[DETECT_RIGHTHAND_AT_MOUTH]);
    
    PVector leftHandToElbow = PVector.sub(lHand,rElbow);
    PVector rightHandToElbow = PVector.sub(rHand,lElbow);
    
    boolean leftNearRightElbow = leftHandToElbow.mag()<300.0f;
    boolean rightNearLeftElbow = rightHandToElbow.mag()<300.0f;
    
    
    detectSignals[DETECT_INTERP_ARMS_CROSSED] = leftNearRightElbow && rightNearLeftElbow;
    
    boolean handsTogether = PVector.sub(rHand,lHand).mag()<120.0f;
    float leftHandHeightFromWaist = abs(lHand.y - lHip.y);
    float rightHandHeightFromWaist = abs(rHand.y - rHip.y);
    float leftHandHeightFromShoulders = abs(lHand.y-lShoulder.y);
    float rightHandHeightFromShoulders = abs(rHand.y-rShoulder.y);
    
    detectSignals[DETECT_INTERP_CUFFED]= handsTogether && (leftHandHeightFromWaist < 100.0f) &&
    (rightHandHeightFromWaist < 100.0f);
    
    detectSignals[DETECT_INTERP_TABLE_WIPER]= (leftHandHeightFromWaist < 220.0f && detectSignals[DETECT_RIGHTHAND_DOWN] 
    && detectSignals[DETECT_LEFTARM_BENT] && waggleLeftHand > 35.0f) ||
    (rightHandHeightFromWaist < 220.0f && detectSignals[DETECT_LEFTHAND_DOWN]
    && detectSignals[DETECT_RIGHTARM_BENT] && waggleRightHand > 35.0f);
    
    //Let's try this one:
    detectSignals[DETECT_INTERP_LOW_DRAW]=(detectSignals[DETECT_RIGHTHAND_DOWN] && waggleLeftHand < 20.0f &&
    detectSignals[DETECT_LEFTHAND_OUT] && leftHandHeightFromWaist<150.0f) 
    ||
    (detectSignals[DETECT_LEFTHAND_DOWN] && waggleRightHand < 20.0f && detectSignals[DETECT_RIGHTHAND_OUT] 
    && rightHandHeightFromWaist<150.0f); 
    
    //Let's try this one: 
    detectSignals[DETECT_INTERP_HIGH_DRAW] = (detectSignals[DETECT_LEFTHAND_OUT] && detectSignals[DETECT_RIGHTHAND_DOWN]
    && waggleLeftHand < 20.0f && leftHandHeightFromWaist>400.0f)
    ||
    (detectSignals[DETECT_RIGHTHAND_OUT] && detectSignals[DETECT_LEFTHAND_DOWN] && waggleRightHand < 20.0f 
    && rightHandHeightFromWaist>400.0f);
    
    //Nope
    detectSignals[DETECT_INTERP_SWEEP] =((leftHandHeightFromShoulders < 250.0f && rightHandHeightFromWaist < 150.0f && waggleRightHand > 30.0f) ||
    (rightHandHeightFromShoulders < 250.0f && leftHandHeightFromWaist < 150.0f && waggleLeftHand > 30.0f))
    && (detectSignals[DETECT_LEFTARM_BENT] && detectSignals[DETECT_RIGHTARM_BENT]);
    
    
    detectSignals[DETECT_INTERP_FIDDLE_WITH_SMALL_THING] =handsTogether && detectSignals[DETECT_LEFTARM_BENT] && detectSignals[DETECT_RIGHTARM_BENT];
    
    //GOING TO NEED TO PASS IN LEG INFO AS WELL FOR THIS TO WORK
    detectSignals[DETECT_INTERP_STAND_LEGS] =false;
    
    //NEED TORSO INFO FOR DIS
    detectSignals[DETECT_INTERP_BOTH_HANDS_BY_CHEST] = false;
    
    
    detectSignals[DETECT_INTERP_ARM_BY_BICEP] =(leftNearRightElbow && detectSignals[DETECT_RIGHTHAND_DOWN]) ||
    (rightNearLeftElbow && detectSignals[DETECT_LEFTHAND_DOWN]);
    
    detectSignals[DETECT_INTERP_HANDS_TOGETHER] = handsTogether;
    
    //Needs a leftArmAtPartner/rightArmAtParnet
    detectSignals[DETECT_INTERP_PLAY_CARD] =(detectSignals[DETECT_RIGHTHAND_IN] && waggleLeftHand > 35.0f&& waggleRightHand<20.0f && lHand.y > 0.0f) ||
    (detectSignals[DETECT_LEFTHAND_IN] && waggleRightHand > 35.0f && waggleLeftHand<20.0f && rHand.y > 0.0f);
    
    detectSignals[DETECT_INTERP_ONE_HAND_UP] =(detectSignals[DETECT_LEFTHAND_UP] && detectSignals[DETECT_RIGHTHAND_DOWN]) ||
    (detectSignals[DETECT_LEFTHAND_DOWN] && detectSignals[DETECT_RIGHTHAND_UP]);
    
    //NEED LEG INFO FOR THIS
    detectSignals[DETECT_MOVING_RUSH_AT] = false;
  }
  
  public void setSkeleton(PVector[] skeleton){
    head=skeleton[0];
    rHand = skeleton[1];
    rWrist=skeleton[2];
    rElbow=skeleton[3];
    rShoulder=skeleton[4];
    rHip=skeleton[5];
    lHand = skeleton[6];
    lWrist=skeleton[7];
    lElbow=skeleton[8];
    lShoulder=skeleton[9];
    lHip = skeleton[10];
  }
  
  
}
class Line{
  public float x1,y1, x2,y2,m;
  public boolean vertical;
  
  Line(float x1, float y1, float x2, float y2){
    //Begin point
    this.x1=x1;
    this.y1=y1;
    //End point
    this.x2=x2;
    this.y2=y2;
    
    //Dunno if I need this yet, we shall see
    vertical=false;
    
    //slope
    //If not vertical
    if(x2!=x1){
      m=(y2-y1)/(x2-x1);
    }
    else{
      vertical=true;
     
      //m=0;
    }
  }
  
  /**
  Based on the intersection of line segments algorithm 
  by Mukesh Prasad in Graphic Gems 2
  
  @returns true if intersects, false otherwise
  */
  public boolean intersect(float a1,float b1,float a2,float b2){
    boolean intersection =false;
    
    //If i) a1,b1 in the line's formula !=0 (r1)
    //ii) a2,b2 in the line's formula !=0 (r2)
    //iii) the signs of r1 and r2 are the same
    //Then lines do not intersect
    float r1 = linesFormula(a1,b1);
    float r2 = linesFormula(a2,b2);
    if(r1!=0 && r2!=0 && sameSign(r1,r2)){
      intersection=false;
    }
    else{
      //If the above failed, we have to check the second part of the algorithm
      Line otherLine = new Line(a1, b1, a2, b2);
      
      //If i)x1,y1, in the other line's formula !=0 (r3)
      //ii) x2,y2 in the other line's formula !=0 (r4)
      //iii) the signs of r3 and r4 are the same
      // Then the lines do not intersect
      float r3 = otherLine.linesFormula(x1,y1);
      float r4 = otherLine.linesFormula(x2,y2);
      if(r3!=0 && r4!=0 && sameSign(r3,r4)){
        intersection=false;
      }
      else{
        intersection=true;
      }
    }
    
    if(a1==x1 && b1==y1){
      intersection=false;
    }
    
    
    return intersection;
  }
  
  //Plugs two points into this line's formula
  public float linesFormula(float a, float b){
    float answer=0.0f;
    //if(!vertical){
      answer = (m*(x1-a))-(y1-b);
    //}
    //else{
    //  answer = y1-b;
    //}
    return answer;
  }
  
  //Returns true if same sign (both positive or negative) false otherwise
  public boolean sameSign(float a, float b){
    boolean sameSign = false;
    if(a>0){
      if(b>0){
        sameSign=true;
      }
    }
    else{
      if(b<0){
        sameSign=true;
      }
    }
    return sameSign;
  }
  
  /** 
  Determines if point occurs between line's
  start and end points
  */
  public boolean collinear(float p, float q){
   PVector a = new PVector(x1,y1);
   PVector c = new PVector (p,q);
   PVector b = new PVector (x2,y2);
   
   
   float crossProduct = (c.y - a.y) * (b.x - a.x) - (c.x - a.x) * (b.y - a.y);

   if(crossProduct!=0){
     return false;
   }
   float dotProduct= (c.x - a.x) * (b.x - a.x) + (c.y - a.y)*(b.y - a.y);
   if(dotProduct<=0){
     return false;
   }
   float squareLength = (b.x - a.x)*(b.x - a.x) + (b.y - a.y)*(b.y - a.y);
   if(dotProduct>squareLength){
     return false;
   }
   
   return true;
  }
  
  public String toString(){
    String result = ("Line from point ("+x1+","+y1+") to point ("+x2+","+y2+")");
    return result;
  }
}
public class PFrame extends Frame {
    public SecondaryApplet s;  
  
    public PFrame() {
        setBounds(100,100,200,400);
        s = new SecondaryApplet();
        add(s);
        s.init();
        show();
    }
    
    public PFrame(String name) {
        this();
        this.setTitle(name);
    }
    
    public PFrame(String name, int w, int h){
      setBounds(100,100,w,h);
        s = new SecondaryApplet(w,h);
        add(s);
        s.init();
        show();
      this.setTitle(name);
    }
    
}
public class SecondaryApplet extends PApplet {
    public FingerDetector fd;
    int w, h;
    
    public SecondaryApplet(){
      super();
    }
    
    public SecondaryApplet(int w, int h){
      super();
      this.w=w;
      this.h=h;
    }
    
    
    public void setup() {
        size(w,h);
        smooth();
        //noLoop();
    }

    public void draw() {
      
    }
} 
  static public void main(String args[]) {
    PApplet.main(new String[] { "--bgcolor=#FFFFFF", "UserScene3d_PlusSkeletonv5" });
  }
}
