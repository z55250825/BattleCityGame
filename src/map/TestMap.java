package map;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/*
 * Path Class:used for Saving Path 
 * from some positions to the HQ
 */
class Path
{
	int x,y;
	Path(){}
	Path(int x,int y){this.x=x;this.y=y;}
}

/*
 * ShootLimit: used for slowing down 
 * AI tank's Bullet Interval between the
 * last bullet and the next bullet
 * 
 * It's target:
 * 
 * After the last bullet hit something or 
 * go out of the map,AI must wait 400ms
 * for the next shoot
 */
class ShootLimit extends Thread
{
	Tank T;
	ShootLimit(){T=null;}
	ShootLimit(Tank T){this.T=T;}
	
	public void run()
	{
		try{
			sleep(400);
		}catch(InterruptedException e){
			System.out.println(e);
		}
		T.shootLimit=false;
	}
}

class Tank extends Thread {
	/*
	 * valid: the tank is dead? 0 dead,>0 not dead
	 * 
	 * x,y: position x:10~530 y:30~550
	 * 
	 * id: which tank texture to use
	 * 
	 * dir: the direction of the tank
	 * 
	 * num: the number code of the tank
	 * the player tank is 10
	 * and other AI tank's number > 10
	 * 
	 * speed: the moving speed of the tank
	 * 
	 * randomStep: the AI random opeartation times at the beginning 
	 * after the randomStep
	 * then the AI will select the shortest path to the HQ
	 * 
	 * nowStep: record the AI's next target position to arrive 
	 * 
	 * shootLimit: judge whether the tank can shoot 
	 * (1)a tank can't shoot the next bullet until the last bullet
	 * hit something or go out of the map
	 * (2)AI tank must wait 400ms to shoot the next bullet 
	 * after one Bullet hit something or go out of the map
	 * 
	 * moveFlag: if move[i]=true means that now the tank is moving 
	 * towards direction i (used for achieve the effect that shooting
	 *  while moving)
	 * 
	 * M:the map M
	 * 
	 * 
	 */
	volatile int valid, x, y, dir;
	int id, num, speed;
	int player_life;
	final static int MAXSTEP=200;
	int randomStep=MAXSTEP;
	int nowStep=-1;
	volatile boolean shootLimit=false;
	volatile boolean moveFlag[]=new boolean[4];
	Map M;
	
	/*
	 * pathRecord: used for recording the 
	 * AI's shortest path to the HQ
	 */
	Vector<Path>pathRecord=new Vector<Path>();
	/*
	 *  dx,dy: tank's moving vector in 4 directions
	 */
	int[] dx,dy;
	
	Tank(){}
	
	Tank(int x,int y,int id,int dir,Map M,int num,int speed, int player_life)
	{
		valid = 1;
		this.x = x;
		this.y = y;
		this.id = id;
		this.dir = dir;
		this.M = M;
		this.num = num;
		this.speed = speed;
		if (this.num==10) this.player_life=player_life;//initialize player_life
		else player_life=1;
		dx=new int[]{0,0,-speed,speed};
		dy=new int[]{-speed,speed,0,0};
		this.shootLimit=false;
		for (int i=0;i<4;++i) moveFlag[i]=false;
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 * 
	 * the main thread method
	 * sleep and move alternatively
	 */
	public void run() 
	{
		{
			while((! M.bStop)&&(this.valid>=1))
			{
				try{
					sleep(TestMap.freshTime*5);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				if (num>10)ai_move();
					else  player_move();
			}
			return;
		}
	}
	
	/*
	 * Transform keyBoard Code to Number
	 */
	final static int keyCodeToNum(int keyboardCode)
	{
		if (keyboardCode==KeyEvent.VK_UP)return 0;
		if (keyboardCode==KeyEvent.VK_DOWN)return 1;
		if (keyboardCode==KeyEvent.VK_LEFT)return 2;
		if (keyboardCode==KeyEvent.VK_RIGHT)return 3;
		if (keyboardCode==KeyEvent.VK_SPACE)return -2;
		return -1;
	}
	
	/*
	 * find the next move direction from the 
	 * moveFlag[] array 
	 */
	int findNextDirection()
	{
		for (int i=0;i<4;++i)
			if (moveFlag[i]==true)
				return i;
		return -1;
	}
	
	/*
	 * player's move control
	 * used in thread to move in cycle
	 */
	void player_move()
	{
		int dir_tmp=findNextDirection();
		if (dir_tmp==-1)return;
		changeState(dir_tmp);
	}
	
	/*
	 * player's move direction control
	 * used in Keyboard listener to control the 
	 * actual move direction
	 * 
	 * when player pressed the keyBoard will
	 * call this function to make the tank
	 * move towards the corresponding direction
	 * 
	 * receive keyboard Code to judge
	 * the operation
 	 */
	void player_move(int keyboardCode)
	{
		int dir_tmp=keyCodeToNum(keyboardCode);
		if (dir_tmp==-1)return;
		if (dir_tmp==-2)
		{
			shoot();
			return;
		}
		moveFlagOn(dir_tmp);
	}
	
	/*
	 * player's move direction control
	 * used in Keyboard listener to control the
	 * actual move direction
	 * 
	 * when player release the keyBoard will
	 * call this function to make the tank
	 * stop moving towards the corresponding
	 * direction
	 * 
	 * receive Keyboard Code to judge
	 * the operation
	 */
	void player_stopMove(int keyboardCode)
	{
		int dir_tmp=keyCodeToNum(keyboardCode);
		if (dir_tmp==-1)return;
		if (dir_tmp==-2)return;
		moveFlagOff(dir_tmp);
	}
	
	/*
	 * control the moveFlag[] array
	 * mark the corresponding position to true
	 */
	void moveFlagOn(int dir)
	{
		int dir_tmp=findNextDirection();
		if (dir_tmp==-1)
		{
			moveFlag[dir]=true;
			return;
		}
		if (dir_tmp!=dir)
		{
			moveFlag[dir_tmp]=false;
			moveFlag[dir]=true;
			return;
		}
		if (dir_tmp==dir)return;
	}
	
	/*
	 * control the moveFlag[] array
	 * mark the corresponding position to false
	 */
	void moveFlagOff(int dir)
	{
		int dir_tmp=findNextDirection();
		if (dir_tmp==-1)return;
		if (dir_tmp==dir)
		{
			moveFlag[dir_tmp]=false;
		}
	}
	
	/*
	 * used for coordinate alignment when tank
	 * turn direction
	 */
	final static int smoothPos(int x,int y,int lastDir,int dir)
	{
		if (dir<=1)
		{
			int tmp=((x-10)/20)*20+10;
			if (lastDir==2)
			{
				if (x-tmp<=10)return tmp;
						else return tmp+20; 
			}
			if (lastDir==3)
			{
				if (x-tmp<10)return tmp;
						else return tmp+20;
			}
			return x;
		}
		else
		{
			int tmp=((y-30)/20)*20+30;
			if (lastDir==0)
			{
				if (y-tmp<=10)return tmp;
						else return tmp+20;
			}
			if (lastDir==1)
			{
				if (y-tmp<10)return tmp;
					 	else  return tmp+20;
			}
			return y;
		}
	}
	
	/*
	 * used for ai move
	 */
	void ai_move()
	{
		shoot();
		if (randomStep==0)
		{
			/*
			 * random turn direction
			 */
			if (suddenTurnDirection())
			{
				pathRecord.clear();
				nowStep=-1;
				randomStep=10;
				return;
			}
			/*
			 * arrive the HQ
			 * random walk near the target
			 */
			if (M.nearHQ((x-10)/20, (y-30)/20))
			{
				pathRecord.clear();
				nowStep=-1;
				randomStep=10;
				changeState((dir+1)%4);
				return;
			}
			
			/*
			 * find the shortest path to the HQ
			 */
			if (pathRecord.isEmpty())
			{
				pathRecord=M.bfsPathToHQ((x-10)/20,(y-30)/20);
				nowStep=pathRecord.size()-1;
			}
			//printPath();
			if (nowStep==-1)
			{
				pathRecord.clear();
				randomStep=10;
				changeState((dir+1)%4);
				return;
			}
			Path tmp=pathRecord.get(nowStep);
			if (betweenTwoTarget())
			{
				nowStep--;
				//System.out.println("step "+nowStep);
				if (nowStep==-1)
				{
					pathRecord.clear();
					nowStep=-1;
					randomStep=10;
					changeState((dir+1)%4);
					return;
				}
				else
					tmp=pathRecord.get(nowStep);
			}
			int dir_tmp=whereToGo(x,y,tmp.x,tmp.y);
			if(changeState(dir_tmp));
			else
			{
				randomStep=10;
				pathRecord.clear();
				changeState((dir+1)%4);
				return;
			}
		}
		else
		{
			if (suddenTurnDirection()){randomStep--;return;}
			if (changeState(dir));
				else randomSelectDirection();
			randomStep--;
		}
	}
	
	/*
	 * when AI is in uncertain status,she will random
	 * select a direction and go 
	 */
	void randomSelectDirection()
	{
		Vector<Integer> move = new Vector<Integer>();
		for(int i=0;i<4;i++)
		{
			int new_x=x+dx[i];
			int new_y=y+dy[i];
			if(!out(new_x,new_y)&&canGoTo(new_x,new_y))
					move.add(i);
		}
		if (move.size()>1)move.removeElement(dir^1);
		if (move.isEmpty());
		else
		{
			int t = (int) (Math.random()*move.size());
			int u = move.elementAt(t);
			changeState(u);
		}
	}
	
	/*
	 * print the AI's shortest Path to HQ
	 */
	void printPath()
	{
		System.out.println("Path");
		for (int i=pathRecord.size()-1;i>=0;--i)
			System.out.println(i+" "+pathRecord.get(i).x+" "+pathRecord.get(i).y);
		System.out.println(nowStep);
		System.out.println("Coordinate: "+x+" "+y);
		//System.out.println("Map "+M.map[5][5]);
		//M.print();
	}
	
	/*
	 * AI's random movement to increase uncertainty
	 */
	boolean suddenTurnDirection()
	{
		double suddenTurnDir_a=Math.random();
		double suddenTurnDir_b=Math.random();
		if (Math.abs(suddenTurnDir_a-suddenTurnDir_b)<=0.01)
		{
			changeState((dir+1)%4);
			return true;
		}
		return false;
	}
	
	/*
	 * judge whether the AI is between two targets points
	 */
	boolean betweenTwoTarget()
	{
		if (nowStep==-1||nowStep==0)return false;
		int ux=pathRecord.get(nowStep).x,uy=pathRecord.get(nowStep).y;
		int vx=pathRecord.get(nowStep-1).x,vy=pathRecord.get(nowStep-1).y;
		if (ux==vx)
		{
			if (x!=ux)return false;
			if (uy<=y&&vy>=y)return true;
			if (uy>=y&&vy<=y)return true;
			return false;
		}
		if (uy==vy)
		{
			if (y!=uy)return false;
			if (ux<=x&&x<=vx)return true;
			if (ux>=x&&x>=vx)return true;
			return false;
		}
		return false;
	}
	
	/*
	 * shoot bullets
	 */
	void shoot()
	{
		if (shootLimit==false)
		{
			shootLimit=true;
			Bullet b=new Bullet(this,M);
			Thread th = new Thread(b);
			synchronized(M.BulletLst)
			{
				M.BulletLst.add(b);
			}
			th.start();
		}
	}
	
	/*
	 * changeState:
	 * synchronized function to
	 * change the tank's x,y,dir
	 * 
	 * dir_tmp:the next move
	 */
	synchronized boolean changeState(int new_dir)
	{
		if (dir!=new_dir)
		{
			int newCoordinate=smoothPos(x,y,dir,new_dir);
			if (new_dir<=1)x=newCoordinate;
					else   y=newCoordinate;
			dir=new_dir;
			return true;
		}
		else
		{
			int new_x=x+dx[dir];
			int new_y=y+dy[dir];
			if (!out(new_x,new_y)&&canGoTo(new_x,new_y))
			{
				x=new_x;
				y=new_y;
				return true;
			}
			else return false;
		}
	}
	
	/*
	 * judge whether (x,y) is out of map
	 */
	final static boolean out(int x,int y)
	{
		if (x<10||x>490) return true;
		if (y<30||y>510) return true;
		return false;
	}
	
	/*
	 * judge whether tank can exist in (x,y) 
	 */
	boolean canGoTo(int x,int y)
	{
		int new_x1,new_y1,new_x2,new_y2;
		new_x1 = (int)Math.ceil(1.0*(x-10)/20);
		new_x2 = (int)Math.floor(1.0*(x-10)/20);
		new_y1 = (int)Math.ceil(1.0*(y-30)/20);
		new_y2 = (int)Math.floor(1.0*(y-30)/20);
		//System.out.println(new_x1+"  "+new_y1+" || "+new_x2+"  "+new_y2);
		if(new_x1>=0&&new_x1+1<26&&new_y1>=0&&new_y1+1<26 && 
				new_x2>=0&&new_x2+1<26&&new_y2>=0&&new_y2+1<26){
			if(M.getMapNum(new_y1,new_x2) == 0 &&
					M.getMapNum(new_y2,new_x1) == 0 &&
					M.getMapNum(new_y1,new_x1) == 0 &&
					M.getMapNum(new_y2,new_x2) == 0 && 
					overlap(num,x,y,M.TankLst) == false)
				return true;
		}
		return false;
	}
	
	/*
	 * judge whether two tank is not overlapped
	 */
	final static boolean overlap(int t,int x,int y,Vector<Tank> TankLst)
	{
		int new_x,new_y;
		synchronized(TankLst)
		{
			for(Tank tank : TankLst){
				if(tank.valid==1)
					if(tank.num != t)
					{
						if (Math.abs(x-tank.x)<40&&Math.abs(y-tank.y)<40)
							return true;
						if (!isIntegerCoordinate(tank))
						{
							int newCoordinate=Tank.smoothPos(tank.x, tank.y, tank.dir, (tank.dir+2)%4);
							if (tank.dir<=1){new_x=tank.x;new_y=newCoordinate;}
										else{new_x=newCoordinate;new_y=tank.y;}
							if (Math.abs(x-new_x)<40&&Math.abs(y-new_y)<40)
								return true;
						}
					}
			}
			return false;
		}
	}
	
	/*
	 * judge whether a coordinate is alignment
	 */
	final static boolean isIntegerCoordinate(Tank t)
	{
		if ((t.x-10)%20==0&&(t.y-30)%20==0)return true;
			return false;
	}
	
	/*
	 * used for AI find the next move direction
	 * when AI already knows the shortest path from
	 * her to the HQ
	 */
	final static int whereToGo(int x,int y,int Targetx,int Targety)
	{
		if (x==Targetx)
		{
			if (y<Targety)return 1;
			if (y>Targety)return 0;
		}
		if (y==Targety)
		{
			if (x<Targetx)return 3;
			if (x>Targetx)return 2;
		}
		if (x<Targetx)return 3;
		if (x>Targetx)return 2;
		return -1;
	}
	
	/*
	 * change the tank's status to dead
	 * and delete the tank
	 */
	void dieStatusChange()
	{
		valid=0;
		Bomb b=new Bomb(x,y);
		M.bombs.add(b);
		if (player_life>0)
		{
			player_life=player_life-1;
			M.deleteTank(this);
			if (num==10) M.newTank(9*20+10,24*20+30,6,0,M,10,4,player_life);
		}
		if (player_life<=0) 
		{		
			M.deleteTank(this);//really~die die die
			if (num==10) M.gameOver();
		}
		//System.out.println(player_life);
	}
	
	/*
	 * to judge if the tank is controlled by player
	 */
	final boolean isPlayer()
	{
		if (num==10)return true;
		return false;
	}
	
	/*
	 * change shootLimit from true to false
	 * to permit the tank to shoot the next bullet
	 * 
	 * (1)if the tank is Player's,then change immediately
	 * (2)if the tank is AI's,then AI needs to wait 400ms
	 * and then change it
	 * 
	 */
	void changeShootLimit()
	{
		if (isPlayer()) shootLimit=false;
		else
		{
			ShootLimit tmp=new ShootLimit(this);
			Thread new_t=new Thread(tmp);
			new_t.start();
		}
	}
	
}

class Bomb
{
	int x,y;
	//炸弹的生命
	int life = 8;
	int period=10;
	boolean isLive = true;
	public Bomb(int x,int y)
	{
		this.x=x;
		this.y=y;
	}
	//减少生命值
	public void lifeDown()
	{
		if (life>1) life--;
		else isLive = false;
	}
}

class Bullet extends Thread
{
	volatile int valid;
	int x, y, dir;
	Map M;
	Tank T;
	int flag;//0代表己方，1代表敌方。
	
	final static int initx[]={20,20,0,40};
	final static int inity[]={0,40,20,20};
	
	Bullet()
	{
		valid=0;x=-1;y=-1;dir=0;flag=0;
	}
	
	Bullet(int x,int y, int dir, Map M, int num)
	{
		valid=1;
		this.dir=dir;
		this.x=x+initx[dir];
		this.y=y+inity[dir];
		this.M=M;
		if (num>10) this.flag=1;
			else this.flag=0;
	}
	
	Bullet(Tank t,Map M)
	{
		valid=1;
		this.dir=t.dir;
		this.x=t.x+initx[dir];
		this.y=t.y+inity[dir];
		this.M=M;
		this.T=t;
		if (t.num>10) this.flag=1;
			else this.flag=0;
	}
	
	public void run() {
		  int move=1;
		  while(! M.bStop){
            if (valid==0) return;
            try{
                sleep(TestMap.freshTime);
            }catch(InterruptedException e){
                System.out.println(e);
            }
            //0向上、1向下、2向左、3向右、4不动
            int dx[] = {0,0,-6,6},dy[] = {-6,6,0,0};
            if(valid==1 && canGoTo(x+dx[dir],y+dy[dir])==true){
                x += dx[dir];
                y += dy[dir];
            }
            else 
            	if (move==0)
            	{
            		dieStatusChange();
            		continue;
            	}
            	else move--;
            
            changeState();
        }
	}
	
	final static int xcoordinateDec[]={-2,-2,0,0};
	final static int ycoordinateDec[]={0,0,-2,-2};
	final static int icoordinateInc[]={0,0,1,1};
	final static int jcoordinateInc[]={1,1,0,0};
	
	void changeState()
	{
	     int j=(int)Math.floor(1.0*(x+xcoordinateDec[dir]-10)/20.0);
         int i=(int)Math.floor(1.0*(y+ycoordinateDec[dir]-30)/20.0);
         i=check(i);j=check(j);
         if (M.isGrass(M.isRiver(M.map[i][j]))>0 || 
        	M.isGrass(M.isRiver(M.map[i+icoordinateInc[dir]][j+jcoordinateInc[dir]]))>0)
        	 dieStatusChange();//打到建筑物
         
         Tank tmp=null;
         Bullet tmpb=null;
         synchronized(M.TankLst)
         {
        	 for (Tank t:M.TankLst)
        	 {
        		 if (t.valid==1)
        		 {
        			 if (x+3>=t.x && t.x+40>=x-3 && y+3>=t.y && t.y+40>=y-3)
        			 {
        				 if ((flag==0 && t.num>10) || (flag==1 && t.num==10))
        				 {	
        					 tmp=t;
        					 break;
        				 }
        			 }
        		 }
        	 }
         }
         
         if (tmp!=null)
         {
        	 tmp.dieStatusChange();
        	 dieStatusChange();
         }
         
         synchronized(M.BulletLst)
         {
        	 for (Bullet b:M.BulletLst)
        	 {
        		 if (b.valid==1)
        		 {
        			 if (x+3>=b.x-3 && b.x+3>=x-3 && y+3>=b.y-3 && b.y+3>=y-3)
        			 {
        				 if (flag!=b.flag)//different sides
        				 {	
        					 tmpb=b;
        					 break;
        				 }
        			 }
        		 }
        	 }
         }
         
         if (tmpb!=null)
         {
        	 tmpb.dieStatusChange();
        	 dieStatusChange();
         }
         
         M.clear(i, j);
         M.clear(i+icoordinateInc[dir], j+jcoordinateInc[dir]);
         if (x<10 || y<30 || x>530 || y>550)dieStatusChange();
	}

	int check(int x)
	{
		int res=x;
		if (x<0) res=0;
		else if (x>25) res=25;
		return res;
	}
	
	boolean canGoTo(int x,int y){
		int new_x1,new_y1,new_x2,new_y2;
		new_x1 = (int)Math.ceil(1.0*(x-10)/20);
		new_x2 = (int)Math.floor(1.0*(x-10)/20);
		new_y1 = (int)Math.ceil(1.0*(y-30)/20);
		new_y2 = (int)Math.floor(1.0*(y-30)/20);
		if(new_x1>=0&&new_x1<=25&&new_y1>=0&&new_y1<=25 &&
				new_x2>=0&&new_x2<=25&&new_y2>=0&&new_y2<=25){
			if(M.map[new_y2][new_x2] == 2) return false;
									else   return true;
		}
		return false;
	}
	
	void dieStatusChange()
	{
		valid=0;
		T.changeShootLimit();
		M.deleteBullet(this);
	}
	
}

class Map extends Frame{
	private static final long serialVersionUID = 1L;
	
	final static int dx[]={0,0,-1,1};
	final static int dy[]={-1,1,0,0};
	
	volatile int map[][] = new int[26][26];
	Vector<Tank> TankLst = new Vector<Tank>();
	Vector<Bullet> BulletLst = new Vector<Bullet>();
	Vector<Path> SeaCoordinate=new Vector<Path>();
	MainThread thread;
	volatile int LeftTank = 10;
	volatile boolean bStop;
	
	Vector<Bomb> bombs = new Vector<Bomb>();
	Image blastImage[]=new Image[8];
	
	class MainThread extends Thread {
		public void run(){
			/*
			 * editor mode on
			 * Map editor 
			 */
			if (editorMode==true)
			{
				while (!bStop)
				{
					repaint();
					try{
						sleep(TestMap.freshTime);
					}catch(InterruptedException e){
						System.out.println(e);
					}
				}
				return;
			}
			
			ImageIcon icon = new ImageIcon();
			for (int i=0;i<8;i++)
			{
				icon=new ImageIcon("pictures/blast"+(i+1)+".gif");
				blastImage[i]=icon.getImage();
			}//Initialize blast images
			
			int new_tank_time = 5*1000,cnt = 10;
			Tank my_tank = new Tank(9*20+10,24*20+30,6,0,Map.this,cnt++,5,3);
			synchronized(TankLst)
			{
				TankLst.add(my_tank);
			}
			Thread th = new Thread(my_tank);
			th.start();
			newTank(10,30,cnt++);newTank(24*20+10,30,cnt++);newTank(12*20+10,30,cnt++);
			while(! bStop){
				repaint();
				out:if(LeftTank > 0 && new_tank_time == 0){
					new_tank_time = 5*1000;
					if(! Tank.overlap(cnt,10,30,TankLst)){
						newTank(10,30,cnt);
						cnt++;
						break out;					
					}
					if(! Tank.overlap(cnt,24*20+10,30,TankLst)){
						newTank(24*20+10,30,cnt);
						cnt++;
						break out;
					}
					if(! Tank.overlap(cnt,12*20+10,30,TankLst)){
						newTank(12*20+10,30,cnt);
						cnt++;
						break out;
					}
				}
				try{
					sleep(TestMap.freshTime);
					new_tank_time -= TestMap.freshTime;
				}catch(InterruptedException e){
					System.out.println(e);
				}
			}
		}
	}
	
	/*
	 * editorX,editorY: the editor position in editor map
	 * 
	 * editorMode: true=Mapeditor mode 
	 * 			   flase=game mode
	 * 
	 * flashTankTime: use for the tank flash in editor
	 * Mode (tank appears and disappears in cycle to 
	 * suggest the editor position)
	 * 
	 * halfFlashTime: the half of flashTankTime
	 * tank appears halfFlashTime units time and
	 * disappears halfFlashTime units time
	 * 
	 * flashTime: record the now time to decide whether
	 * the tank is appeared or disappeared
	 * 
	 */
	volatile int editorX,editorY;
	boolean editorMode;
	final static int flashTankTime=50;
	final static int halfFlashTankTime=flashTankTime/2;
	int flashTime=flashTankTime;
	int findState;
	
	/*
	 * if use Map() then map editor mode
	 */
	Map(){
		editorMode=true;
		findState=0;
		editorX=0;editorY=0;
		
		for (int i=0;i<26;++i)
			for (int j=0;j<26;++j)
				map[i][j]=0;
		
		map[24][12]=map[24][13]=map[25][12]=map[25][13]=5;
		map[23][11]=map[24][11]=map[25][11]=map[23][12]=map[23][13]
				=map[23][14]=map[24][14]=map[25][14]=1;
		this.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				bStop = true;
				System.exit(0);
			}
		});
		
		this.addKeyListener(new KeyAdapter(){
			public void keyPressed(KeyEvent e){
				editorEvent(e.getKeyCode());
			}
		});
		
		this.setSize(650, 560);
		this.setBackground(Color.black);
		this.setVisible(true);
		thread=new MainThread();
		thread.start();
	}
	
	/*
	 * judge whether the KeyboardCode is UP,DOWN,LEFT,RIGHT
	 * if it is ,return the corresponding direction number
	 * 		else return -1
	 */
	int isDirectionCode(int KeyboardCode)
	{
		if (KeyboardCode==KeyEvent.VK_UP)return 0;
		if (KeyboardCode==KeyEvent.VK_DOWN)return 1;
		if (KeyboardCode==KeyEvent.VK_LEFT)return 2;
		if (KeyboardCode==KeyEvent.VK_RIGHT)return 3;
		return -1;
	}
	
	final static int editorDx[]={-2,2,0,0};
	final static int editorDy[]={0,0,-2,2};
	
	/*
	 * editorEvent:
	 *  receive the KeyEvent and deal with it
	 *  according to the KeyboardCode
	 *  
	 *  if it's DirectionCode,then move editor position
	 *  
	 *  if it's S,then save the editor map
	 *  
	 *  if it's Space,then change the texture
	 *  on the editor position
	 */
	void editorEvent(int KeyboardCode)
	{
		int tmp;
		if ((tmp=isDirectionCode(KeyboardCode))!=-1)
		{
			editorX=(editorX+editorDx[tmp]+26)%26;
			editorY=(editorY+editorDy[tmp]+26)%26;
			return;
		}
		if (KeyboardCode==KeyEvent.VK_S)
		{
			saveEditMap();
			return;
		}
		if (KeyboardCode==KeyEvent.VK_SPACE)
		{
			editorChangeState();
			return;
		}
	}
	
	/*
	 * editorState: used for edit map
	 * 2x2 grids's State saved in a 4-digit number
	 * 1234 means that the left up grid is 1,
	 * 				   the right up grid is 2,
	 * 				   the left down grid is 3,
	 * 				   the right down grid is 4.
	 */
	final static int editorState[]={0,1111,1100,1010,11,101,2222,2200,2020,22,202,
			3333,4444,1122,1212,2211,2121};
	final static int editorStateNum=17;
	
	/*
	 * calculate the State number of the 2x2 grids
	 * when the left up grid is (x,y)
	 */
	int editorCalculateState(int x,int y)
	{
		return map[x][y]*1000+map[x][y+1]*100+map[x+1][y]*10+map[x+1][y+1];
	}
	
	/*
	 * used for changing State of 
	 * the 2x2grids ((editorX,editorY) is the left up grid)
	 * '
	 */
	void editorChangeState()
	{
		//HeadQuarter Texture is always 5
		if (editorX==24&&editorY==12)return;
		
		int nowState=editorCalculateState(editorX,editorY);
		int nowFindState=0;
		for (;nowFindState<editorStateNum;++nowFindState)
			if (editorState[nowFindState]==nowState)
				break;
		if (nowFindState!=findState)
		{
			int newState=editorState[findState];
			map[editorX][editorY]=newState/1000;
			map[editorX][editorY+1]=(newState/100)%10;
			map[editorX+1][editorY]=(newState/10)%10;
			map[editorX+1][editorY+1]=newState%10;
			return;
		}
		findState=(findState+1)%editorStateNum;
		int newState=editorState[findState];
		map[editorX][editorY]=newState/1000;
		map[editorX][editorY+1]=(newState/100)%10;
		map[editorX+1][editorY]=(newState/10)%10;
		map[editorX+1][editorY+1]=newState%10;
		return;
	}
	
	void saveEditMap()
	{
		try{
			FileWriter out=new FileWriter("Maps/Map0.txt");
			BufferedWriter wt=new BufferedWriter(out);
			for (int i=0;i<26;++i)
			{
				for (int j=0;j<26;++j)
					wt.write(map[i][j]+" ");
				wt.newLine();
			}
			wt.close();
		}catch(IOException e){
			System.out.print(e);
		}
	}
	
	Map(int level){
		editorMode=false;
		try{
			File f = new File("Maps");
			File fs[] = f.listFiles();
			File mission=null;
			for (File F : fs){
				if (F.getName().equals("Map"+level+".txt"))
					mission=F;
			}
			FileReader in = new FileReader(mission);
			BufferedReader rd = new BufferedReader(in);
			for(int i=0;i<26;i++){
				String str[] = rd.readLine().split(" ");
				for(int j=0;j<26;j++){
					map[i][j] = Integer.parseInt(str[j]);
					if (map[i][j]==3)
						SeaCoordinate.add(new Path(i,j));
				}
			}
			rd.close();
		}catch(IOException e){
			System.out.println(e);
		}
		this.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				bStop = true;
				System.exit(0);
			}
		});
		/*
		 * keyboard listener
		 */
		this.addKeyListener(new KeyAdapter(){
			public void keyPressed(KeyEvent e)
			{
				synchronized(TankLst)
				{
					for (Tank tank:TankLst)
					{
						if (tank.num==10)
							tank.player_move(e.getKeyCode());
					}
				}
			}
			public void keyReleased(KeyEvent e)
			{
				synchronized(TankLst)
				{
					for (Tank tank:TankLst)
					{
						if (tank.num==10)
							tank.player_stopMove(e.getKeyCode());
					}
				}
			}
		});
		this.setSize(650, 560);
		this.setBackground(Color.black);
		this.setVisible(true);
		thread = new MainThread();
		thread.start();
	}
	
	void editorPaint(Graphics g)
	{
		String path = "pictures";
		for (int i=0;i<26;++i)
			for (int j=0;j<26;++j)
				if (map[i][j]!=0&&map[i][j]!=5)
				{
					String dir = path + "/" + map[i][j] + ".gif";
					ImageIcon icon = new ImageIcon(dir);
					Image images = icon.getImage();
					g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
				}
		String dir = path + "/"+"symbol.gif";
		ImageIcon icon = new ImageIcon(dir);
		Image images = icon.getImage();
		g.drawImage(images, 10+12*20, 30+24*20, 40, 40,this);
		if (editorMode)
		{
			if (flashTime>=halfFlashTankTime)
			{
				dir=path+"/"+"60.gif";
				icon=new ImageIcon(dir);
				images=icon.getImage();
				g.drawImage(images,	10+editorY*20, 30+editorX*20, 40,40,this);
				flashTime--;
			}
			else
			{
				flashTime--;
				if (flashTime==-1)
					flashTime=flashTankTime;
			}
		}
	}
	
	public void paint(Graphics g){
		super.paint(g);
		g.setColor(Color.white);
		g.drawLine(10, 30, 530, 30);
		g.drawLine(10, 30, 10, 550);
		g.drawLine(530, 30, 530, 550);
		g.drawLine(10, 550, 530, 550);
		if (editorMode==true)
		{
			editorPaint(g);
			return;
		}
		String path = "pictures";
		for (Path Sea : SeaCoordinate)
		{
			int i=Sea.x,j=Sea.y;
			String dir=path+"/3.gif";
			ImageIcon icon=new ImageIcon(dir);
			Image images=icon.getImage();
			g.drawImage(images, 10+j*20, 30+i*20, 20,20,this);
		}
		paintTank(g);
		paintBullet(g);
		paintBlast(g);
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++){
				if(map[i][j] != 0 && map[i][j] != 5 &&map[i][j] !=3){
					String dir = path + "/" + map[i][j] + ".gif";
					ImageIcon icon = new ImageIcon(dir);
					Image images = icon.getImage();
					g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
				}
			}
		}
		String dir = path + "/"+"symbol.gif";
		ImageIcon icon = new ImageIcon(dir);
		Image images = icon.getImage();
		g.drawImage(images, 10+12*20, 30+24*20, 40, 40,this);
		if (editorMode)
		{
			if (flashTime>=halfFlashTankTime)
			{
				dir=path+"/"+"60.gif";
				icon=new ImageIcon(dir);
				images=icon.getImage();
				g.drawImage(images,	10+editorY*20, 30+editorX*20, 40,40,this);
				flashTime--;
			}
			else
			{
				flashTime--;
				if (flashTime==-1)
					flashTime=flashTankTime;
			}
		}
		//print();
	}
	
	void paintBlast(Graphics g)
	{
		for(int i=0;i<bombs.size();i++)
		{
	        //取出炸弹
	        Bomb b = bombs.get(i);
	        g.drawImage(blastImage[b.life-1], b.x, b.y, 40, 40, this);
	        //System.out.format("%d\n",b.life);
	        b.lifeDown();
	        //如果life=0，将炸弹从bombs向量去掉
	        if(b.isLive==false) bombs.remove(b);
	    }
	}
	
	void paintTank(Graphics g)
	{
		String path = "pictures";
		synchronized(TankLst)
		{
			for(Tank t : TankLst){
				if(t.valid == 0) continue;
				String dir = path + "/" + t.id + t.dir + ".gif";
				ImageIcon icon = new ImageIcon(dir);
				Image images = icon.getImage();
				g.drawImage(images,t.x, t.y,40, 40, this);
			}
		}
	}

	void paintBullet(Graphics g){
		String path = "pictures";
		synchronized(BulletLst)
		{
			for(Bullet b : BulletLst){
				if (b.valid == 0) continue;
				String dir = path + "/" + "bullet.gif";
				ImageIcon icon = new ImageIcon(dir);
				Image images = icon.getImage();
				g.drawImage(images,b.x, b.y, 5, 5, this);
			}
		}
	}
	
	int isGrass(int n)
	{
		if (n==4)return 0;
			else return n; 
	}
	
	int isRiver(int n)
	{
		if (n==3)return 0;
			else return n; 
	}
	
	synchronized int getMapNum(int i,int j){
		return isGrass(map[i][j])+isGrass(map[i+1][j])
		+isGrass(map[i][j+1])+isGrass(map[i+1][j+1]);
	}

	/*
	 * print Map information
	 */
	void print(){
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++)
				System.out.print(map[i][j] + " ");
			System.out.println();
		}
		System.out.println();
	}
	
	/*
	 * AI bfs
	 */
	Vector<Path> bfsPathToHQ(int x,int y)
	{
		int vis[][]=new int[26][26];
		Path pre[][]=new Path[26][26];
		Vector<Path>s=new Vector<Path>();
		pre[x][y]=new Path(-1,-1);
		vis[x][y]=1;
		Queue<Path>que=new LinkedList<Path>();
		que.offer(new Path(x,y));
		while (!que.isEmpty())
		{
			Path u=que.poll();
			int ux=u.x,uy=u.y;
			for (int dir=0;dir<4;++dir)
			{
				int vx=ux+dx[dir];
				int vy=uy+dy[dir];
				if (!out(vx,vy)&&canGoTo(vx,vy)&&vis[vx][vy]==0)
				{
					pre[vx][vy]=new Path(ux,uy);
					vis[vx][vy]=1;
					if (nearHQ(vx,vy))
					{
						while (vx!=x||vy!=y)
						{
							s.addElement(new Path(vx*20+10,vy*20+30));
							Path tmp=pre[vx][vy];
							vx=tmp.x;
							vy=tmp.y;
						}
						s.addElement(new Path(x*20+10,y*20+30));
						return s;
					}
					que.offer(new Path(vx,vy));
				}
			}
		}
		s.clear();
		return s;
	}
	
	/*
	 * map (x,y) is out of border?
	 */
	boolean out(int x,int y)
	{
		if (x<0||x>=25)return true;
		if (y<0||y>=25)return true;
		return false;
	}

	/*
	 * map[x][y] is a reachable area?
	 */
	boolean canGoTo(int x,int y)
	{
		if (isGrass(map[y][x])==0&&isGrass(map[y][x+1])==0&&
				isGrass(map[y+1][x])==0&&isGrass(map[y+1][x+1])==0)return true;
		return false;
	}
	
	/*
	 * map[x][y] is close to the HQ?
	 */
	boolean nearHQ(int x,int y)
	{
		if (x==9&&y==24)return true;
		if (x==15&&y==24)return true;
		if (y==21&&x==12)return true;
		return false;
	}
	
	int attackHQ(int x,int y)
	{
		if (x==9&&y==24)return 3;
		if (x==15&&y==24)return 2;
		if (y==21&&x==12)return 1;
		return 0;
	}
	
	/*
	boolean overlap(int x,int y)
	{
		synchronized(TankLst)
		{
			for(Tank tank : TankLst){
				if(Math.abs(tank.x-x)<45 && Math.abs(tank.y-y)<45)
					return true;
			}
			return false;
		}
	}*/
	
	void newTank(int x, int y, int id, int dir, Map M, int num, int speed, int player_life)
	{
		Tank new_tank = new Tank(x,y,id,dir,this,num,speed,player_life);//speed=4
		synchronized(TankLst)
		{
			TankLst.add(new_tank);
		}
		Thread new_th = new Thread(new_tank);
		new_th.start();
	}
	
	void newTank(int x,int y, int cnt)
	{
		Tank new_tank = new Tank(x,y,7,1,this,cnt,4,3);
		synchronized(TankLst)
		{
			TankLst.add(new_tank);
		}
		Thread new_th = new Thread(new_tank);
		new_th.start();
		LeftTank--;
	}
	
	/*
	 * delete tank T from the TankLst
	 * synchronized 
	 */
	void deleteTank(Tank t)
	{
		synchronized(TankLst)
		{
			TankLst.removeElement(t);
		}
	}
	
	/*
	 * delete Bullet B from the BulletLst
	 * synchronized
	 */
	void deleteBullet(Bullet b)
	{
		synchronized(BulletLst)
		{
			BulletLst.removeElement(b);
		}
	}

	int check(int x)
	 {
		int res=x;
		if (x<0) res=0;
		else if (x>25) res=25;
		return res;
	 }
	
	void clear(int i,int j)
	 {
		i=check(i);
		j=check(j);
		if(map[i][j] == 1) map[i][j] = 0;
		else if(map[i][j] == 2) map[i][j] = 2;
		else if(map[i][j] == 5) gameOver();
	 }
	
	void gameOver()
	{
		bStop = true;
	}
}

public class TestMap {
	final static int freshTime=25;
	public static void main(String[] args) {
		Map M = new Map(10);
	}
}
