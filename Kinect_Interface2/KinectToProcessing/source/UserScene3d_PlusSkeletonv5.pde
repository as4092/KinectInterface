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
 
 

import SimpleOpenNI.*;
import oscP5.*;
import netP5.*;

OscP5 oscP5;
NetAddress myRemoteLocation;
SimpleOpenNI context;

int currUser;
float        zoomF =0.5f;
float        rotX = radians(180);  // by default rotate the whole scene 180deg around the x-axis, 
// the data from openni comes upside down
float        rotY = radians(0);
//ALL GREEN NOW
color[]      userColors = { 
  color(0, 255, 0), color(0, 255, 0), color(0, 255, 00), color(0, 255, 0), color(0, 255, 0), color(0, 255, 0)
};
color[]      userCoMColors = { 
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

void setup() {
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
  float(width)/float(height), 
  10, 150000);
  
}


void draw()
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

void rightHandBother(){
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

void leftHandBother(){
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
boolean excludeFromRight(PVector pixel) {
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
boolean excludeFromLeft(PVector pixel) {
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
boolean checkProximity(PVector pixel, PVector check, float distWithin) {
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

void drawCircleForWrist(PVector wristPos) {
  //DRAWING THE WRIST
  // convert real world point to projective space
  PVector jointPos_Proj = new PVector(); 
  context.convertRealWorldToProjective(wristPos, jointPos_Proj);
  wristPos.z=0;

  fill(0, 0, 255);
  // draw the circle at the position of the head with the hand size scaled by the distance scalar
  ellipse(jointPos_Proj.x, jointPos_Proj.y, 50, 50);
}

void establishLeftBox() {
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

void establishRightBox() {
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

boolean checkLeftExtension() {
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


PVector findWrist(PVector h, PVector e, PVector s) {
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

void sendJointPosition(int userId)
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
void circleForAHead(int userId)
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
void circleForRightHand(int userId)
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
void circleForLeftHand(int userId)
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
void drawSkeleton(int userId)
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
void onNewUser(int userId)
{
  println("New User Detected - userId: " + userId);

  // start pose detection
  context.startPoseDetection("Psi", userId);
}

// when a person ('user') leaves the field of view 
void onLostUser(int userId)
{
  println("User Lost - userId: " + userId);
}

// when a user begins a pose
void onStartPose(String pose, int userId)
{
  println("Start of Pose Detected  - userId: " + userId + ", pose: " + pose);

  // stop pose detection
  context.stopPoseDetection(userId); 

  // start attempting to calibrate the skeleton
  context.requestCalibrationSkeleton(userId, true);
}

// when calibration begins
void onStartCalibration(int userId)
{
  println("Beginning Calibration - userId: " + userId);
}

// when calibaration ends - successfully or unsucessfully 
void onEndCalibration(int userId, boolean successfull)
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

void keyPressed()
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
      if (zoomF < 0.01)
        zoomF = 0.01;
    }
    else
      rotX -= 0.1f;
    break;
  }
}
