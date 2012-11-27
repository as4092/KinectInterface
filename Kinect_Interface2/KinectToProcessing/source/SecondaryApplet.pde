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
