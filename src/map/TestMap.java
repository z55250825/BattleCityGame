package map;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import sun.audio.*;

/*
 * important parameter
 * 
 * 1) freshTime(TestMap Class)(used for paint period)
 * 
 * 2) Maxstep(Tank Class)(determine the AI's beginning movement)
 * 
 * 3) bombPeriod(Bomb Class)(determine the Bomb dynamic effect)
 * 
 * 4) saintPeriod(Saint Class)(determine the Saint dynamic effect)
 * 
 * 5) flashTankTime(Map Class)(determine the tank's flash period
 * int map editor Mode)(flash tank used for determining the position
 * in editor mode)
 * 
 * 6) seaStreamPeriod(Map Class)(determine the sea's flow dynamic effect)
 * 
 * 7) GameOverTime(GameOverCount Class)(used for show GameOver gif)
 * 
 * 8) shieldFlashTime(Tank Class)(used for paint flash shield)
 * 
 * 9) tankFlashTime(Tank Class)(used for paint flash tank)
 */

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
 * when need music play, create a new thread class
 * MusicPlayer to play the music
 */
class MusicPlayer extends Thread
{
	String path;
	MusicPlayer(){path="";}
	MusicPlayer(String name){this.path=name;}
	public void run()
	{
		Map.playStartMusic(path);
	}
}

/*
 * when prop tank was hit will generate a prop
 * on map , only one prop exists at the same time
 */
class Props extends Thread {
	/*
	 * number = 
	 * 0: shield
	 * 		get a protect shield for 12s
	 * 1: steel guard
	 * 		make the hq from brick to steel
	 * 2: player_life
	 * 		player's life +1s
	 * 3: level up
	 * 	 	first level up: bullet speed up
	 * 		second level up: can shoot 2 bullet one time
	 * 				meanwhile tank change texture
	 * 		third level up: can use enforced bullet
	 * 		(this bullet can break steel brick)
	 * 				meanwhile tank change texture
	 * 		the next will have no effect
	 * 4: bomb
	 * 		kill all the enemy tank on the map
	 * 5: clock up
	 * 		make all the enemy tank stop for 15s
	 */
	volatile int number,x,y;//number:prop type;x,y:coordinate
	final static int propFlashTime=10;//used for controlling prop flash
	final static int halfPropFlashTime=propFlashTime/2;
	final static int frozeTime=25000;//used for controlling prop stay time
	int froze;//prop stay on the map for froze s
	int propFlash;
	volatile boolean valid;//is prop still can be used?
	
	Map M;
	Props(){}
	
	//random generate a prop,random type,random coordinate
	Props(Map M)
	{
		valid=true;
		this.M=M;
		number=((int)(Math.random()*600))%6;
		x=(int)(Math.random()*26);
		y=(int)(Math.random()*26);
		froze=(int)(Math.random()*frozeTime)+8000;
		if ((x&1)==0)++x;if (x==25)x=23;
		if ((y&1)==0)++y;if (y==25)y=23;
		int times=0;
		while (times<10&&Map.canDropProp(x, y, M)==false)
		{
			x=(int)(Math.random()*26);
			y=(int)(Math.random()*26);
			if ((x&1)==0)++x;if (x==25)x=23;
			if ((y&1)==0)++y;if (y==25)y=23;
			times++;
		}
		x=x*20+30;
		y=y*20+10;
		synchronized(M.props)
		{
			M.props.clear();
			M.props.addElement(this);
		}
		Thread new_t=new Thread(this);
		new_t.start();
		Map.playMusic("eat");
	}
	
	public void run()
	{
		try{
			sleep(froze);
		}catch(InterruptedException e){
			System.out.println(e);
		}
		dieStatusChange();
	}
	
	//prop become not valid
	void dieStatusChange()
	{
		if (valid==false)return;
		valid=false;
		synchronized(M.props)
		{
			M.props.removeElement(this);
		}
	}
	
	//used for prop flashing
	void flashDown()
	{
		propFlash--;
		if (propFlash<0)
			propFlash=Props.propFlashTime;
	}
	
	//Tank t use this prop
	void use(Tank t)
	{
		/*
		 * ClockUp class:
		 * used for controlling enemy tank stop moving
		 */
		class ClockUp extends Thread{
			public void run()
			{
				Tank.aiMoveStatusChange(1);
				try{
					sleep(Tank.aiSleepTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				Tank.aiMoveStatusChange(-1);
			}
		}
		if (valid==false)return;
		switch(number){
			case 0:
				t.shieldModeOn();
				Map.playMusic("add");
				break;
			case 1:
				M.steelGuard();
				Map.playMusic("add");
				break;
			case 2:
				t.playerLifeAdd();
				Map.playMusic("life");
				break;
			case 3:
				t.playerLevelUp();
				Map.playMusic("add");
				break;
			case 4:
				M.killAllEnemyTank();
				Map.playMusic("bomb");
				break;
			case 5:
				ClockUp cu=new ClockUp();
				Thread new_t=new Thread(cu);
				new_t.start();
				Map.playMusic("add");
				break;
		}
		dieStatusChange();
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
	 * randomStep=MAXSTEP
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
	 * aiCanNotMove: used for clockUp prop,determine whether the 
	 * AI can move.
	 * 
	 * moveFlag: if move[i]=true means that now the tank is moving 
	 * towards direction i (used for achieve the effect that shooting
	 *  while moving)
	 *  
	 *  shieldMode: determine whether the tank is protected by shield
	 *  shieldFlashTime/halfShieldFlashTime: constant used for shield flash
	 *  shieldFlash: used for controlling shield flash
	 *  bornShieldTime(ms):determine the shield time when player is born
	 *  battleShieldTime(ms):determine the shield time when player eat a shield prop 
	 *  
	 *  tanklevel:used for levelUp props,props make player's tank stronger accoring to
	 *  the current level
	 *  
	 *  bulletSpeed:determine the speed of the bullet that the tank shoots.
	 *  enforcedBullet: determine the bullet type of the tank,if it's enforced
	 *  if can destroy steel block
	 *  
	 *  tankFlashTime/halfTankFlashTime:constant used for special tank flash
	 *  tankFlash:used for controlling tank flash
	 * 
	 * 	M:the map M
	 * 
	 * 
	 */
	volatile int valid, x, y, dir;
	volatile int num, speed;
	int id;
	volatile int player_life;//can inherit
	
	final static int MAXSTEP=120;
	int randomStep=MAXSTEP;
	int nowStep=-1;
	volatile static int aiCanNotMove=0;
	final static int aiSleepTime=15000;
	
	volatile int maxShootLimit=1;
	volatile int shootLimit=1;
	
	volatile boolean moveFlag[]=new boolean[4];
	
	volatile int shieldMode=0;
	final static int shieldFlashTime=4;
	final static int halfShieldFlashTime=shieldFlashTime/2;
	int shieldFlash=shieldFlashTime;
	final static int bornShieldTime=5000;
	final static int battleShieldTime=12000;
	
	final static int initialBulletSpeed=6;
	final static int enforcedBulletSpeed=8;
	volatile int bulletSpeed=6;
	volatile boolean enforcedBullet=false;
	
	volatile int tankLevel;//can inheit
	
	final static int tankFlashTime=4;
	final static int halftankFlashTime=tankFlashTime/2;
	int tankFlash=tankFlashTime;
	
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
		//s.entertainment();
		if (id==10||id==12)valid = 4;
			  		  else valid = 1;
		this.x = x;
		this.y = y;
		this.id = id;
		this.dir = dir;
		this.M = M;
		this.num = num;
		this.speed = speed;
		if (this.num==10) this.player_life=player_life;//initialize player_life
					else this.player_life=1;
		dx=new int[]{0,0,-speed,speed};
		dy=new int[]{-speed,speed,0,0};
		this.shootLimit=1;
		this.bulletSpeed=Tank.initialBulletSpeed;
		if (this.id==8||this.id==14)
			this.bulletSpeed=Tank.enforcedBulletSpeed;
		this.enforcedBullet=false;
		this.tankLevel=1;
		for (int i=0;i<4;++i) moveFlag[i]=false;
	
		class bornShieldModeControl extends Thread{
			public void run(){
				try{
					sleep(Tank.bornShieldTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				shieldMode--;
			}
		}
		if (this.isPlayer())
		{
			M.playerLifeCount=this.player_life;
			this.shieldMode=1;
			bornShieldModeControl shield=new bornShieldModeControl();
			Thread new_t=new Thread(shield);
			new_t.start();
		}
	}
	
	Tank(int x,int y,int id,int dir,Map M,int num,int speed, int player_life,int level)
	{
		valid=1;
		this.x = x;
		this.y = y;
		this.id = id;
		this.dir = dir;
		this.M = M;
		this.num = num;
		this.speed = speed;
		this.player_life=player_life;
		dx=new int[]{0,0,-speed,speed};
		dy=new int[]{-speed,speed,0,0};
		if (level<=2){this.shootLimit=this.maxShootLimit=1;}
				else {this.shootLimit=this.maxShootLimit=2;}
		if (level==1)this.bulletSpeed=Tank.initialBulletSpeed;
				else this.bulletSpeed=Tank.enforcedBulletSpeed;
		if (level<4)this.enforcedBullet=false;
			  else  this.enforcedBullet=true;
		this.tankLevel=level;
		for (int i=0;i<4;++i) moveFlag[i]=false;
	
		class bornShieldModeControl extends Thread{
			public void run(){
				try{
					sleep(Tank.bornShieldTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				shieldMode--;
			}
		}
		this.shieldMode=1;
		bornShieldModeControl shield=new bornShieldModeControl();
		Thread new_t=new Thread(shield);
		new_t.start();
		M.playerLifeCount=this.player_life;
	}
	
	/*
	 * add protected shield for the tank
	 */
	final void shieldModeOn()
	{
		class battleShieldModeControl extends Thread{
			public void run(){
				try{
					sleep(Tank.battleShieldTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				shieldMode--;
			}
		}
		shieldMode++;
		battleShieldModeControl shield=new battleShieldModeControl();
		Thread new_t=new Thread(shield);
		new_t.start();
	}
	
	/*
	 * to know whether the tank has shield 
	 */
	final boolean isShieldOn()
	{
		return shieldMode>0;
	}
	
	final void shieldDown()
	{
		this.shieldFlash--;
		if (this.shieldFlash<0)
			this.shieldFlash=Tank.shieldFlashTime;
	}
	
	/*
	 * used for special flash tank's flashing effect,
	 * for example,prop tank
	 */
	final void tankFlashDown()
	{
		this.tankFlash--;
		if (this.tankFlash<0)
			this.tankFlash=Tank.tankFlashTime;
	}
	
	/*
	 * used for player's tank,if there is a prop
	 * near the player then player will get the prop
	 * and use it
	 */
	final void pickProps()
	{
		Props p=null;
		synchronized(M.props)
		{
			for (Props props : M.props)
				if (props.valid)
					if (Math.abs(x-props.y)<40&&Math.abs(y-props.x)<40)
						p=props;
		}
		if (p!=null)p.use(this);
	}
	
	/*
	 * add player's life
	 */
	final void playerLifeAdd()
	{
		this.player_life++;
		M.playerLifeCount=player_life;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 * main Thread of a tank
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
				if (M.pause==false)
				{
					if (num>10)
					{
						if (aiCanNotMove==0)
							ai_move();
					}
					else  
					{
						player_move();
						pickProps();
					}
				}
				else
					if (isPlayer())
					{
						for (int i=0;i<4;++i)
							moveFlag[i]=false;
					}
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
		if (M.Over)return;
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
	 * change the aiCanNotMove;used for ai move controlling
	 */
	final static void aiMoveStatusChange(int dir)
	{
		aiCanNotMove=aiCanNotMove+dir;
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
			if (Map.nearHQ((x-10)/20, (y-30)/20))
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
	 * print the AI's shortest Path to HQ,used for debugging
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
		if (shootLimit>=1)
		{
			shootLimit--;
			if (this.isPlayer())Map.playMusic("fire");
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
					overlap(num,x,y,M.TankLst,M.saints) == false)
				return true;
		}
		return false;
	}
	
	/*
	 * judge whether two tank is not overlapped
	 */
	final static boolean overlap(int t,int x,int y,Vector<Tank> TankLst,Vector<Saint> SaintLst)
	{
		int new_x,new_y;
		synchronized(TankLst)
		{
			for(Tank tank : TankLst){
				if(tank.valid>=1)
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
		}
		synchronized(SaintLst)
		{
			for(Saint saint : SaintLst)
				if (saint.isLive==true)
				{
					//saint出生处一定是整点，不考虑对齐
					if (Math.abs(x-saint.x)<40&&Math.abs(y-saint.y)<40)
						return true;
				}
		}
		return false;
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
	
	final static boolean isPropTank(int number)
	{
		if (number==11)return true;
		if (number==12)return true;
		if (number==13)return true;
		if (number==14)return true;
		return false;
	}
	
	/*
	 * change the tank's status to dead
	 * and delete the tank
	 */
	synchronized void dieStatusChange(boolean bulletHit)
	{
		if (this.isShieldOn())return;
		if (valid>1&&bulletHit==true)
		{
			valid--;
			if (isPropTank(this.id)&&valid==3)
			{
				Props newProp=new Props(this.M);
			}
			return;
		}
		valid=0;
		Bomb b=new Bomb(x,y);
		M.bombs.add(b);
		Map.playMusic("blast");
		player_life=player_life-1;
		M.deleteTank(this);
		if (this.isPlayer()==false)
		{
			M.deadEnemyTank++;
		}
		if (id!=12&&bulletHit&&isPropTank(this.id))
		{
			Props newProp=new Props(this.M);
		}
		if (isPlayer())
		{
			M.playerLifeCount=player_life;
			if (player_life>0)
				M.NewTank(170,510,6,0,M,10,8,player_life);
			else
				M.gameOver(false);
		}
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
			ShootLimit(Tank T){this.T=T;}
			
			public void run()
			{
				try{
					sleep(500);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				T.shootLimit++;
			}
		}
		if (isPlayer()) shootLimit++;
		else
		{
			ShootLimit tmp=new ShootLimit(this);
			Thread new_t=new Thread(tmp);
			new_t.start();
		}
	}

	/*
	 * used for player's level up
	 */
	void playerLevelUp()
	{
		//first make bullet speed faster
		if (bulletSpeed==Tank.initialBulletSpeed)
		{
			tankLevel++;
			bulletSpeed=Tank.enforcedBulletSpeed;
			return;
		}
		//second make shoot bullet 2
		if (maxShootLimit==1)
		{
			tankLevel++;
			maxShootLimit++;
			shootLimit++;
			return;
		}
		//make bullet enforced
		if (enforcedBullet==false)
		{
			tankLevel++;
			enforcedBullet=true;
			return;
		}
	}
}

class Bomb
{
	int x,y;
	//炸弹的生命
	int life = 8;
	final static int bombPeriod=1;
	int period=bombPeriod;
	boolean isLive = true;
	public Bomb(int x,int y)
	{
		this.x=x;
		this.y=y;
	}
	//减少生命值
	public void lifeDown()
	{
		period--;
		if (period==0)
		{
			period=bombPeriod;
			if (life>1) life--;
				else isLive = false;
		}
	}
}

class Saint
{
    int x,y;
    int life=4;
    final static int saintPeriod=6;
    int period=saintPeriod;
    volatile boolean isLive=true;
    
    public Saint(int x,int y)
    {
        this.x=x;
        this.y=y;
    }
    
    public void lifeDown()
    {
    	period--;
    	if (period==0)
    	{
    		period=saintPeriod;
    		if (life>1) life--;
    			else 
    			{
    				isLive = false;
    			}
    	}
    }
}

class Bullet extends Thread
{
	volatile int valid;
	int x, y, dir;
	Map M;
	Tank T;
	int flag;//0代表己方，1代表敌方。
	int speed;
	boolean enforced;
	
	final static int initx[]={20,20,0,40};
	final static int inity[]={0,40,20,20};
	
	Bullet()
	{
		valid=0;x=-1;y=-1;dir=0;flag=0;
		this.speed=6;
	}
	
	Bullet(int x,int y, int dir, Map M, int num,int speed,boolean enforced)
	{
		valid=1;
		this.dir=dir;
		this.x=x+initx[dir];
		this.y=y+inity[dir];
		this.M=M;
		this.speed=speed;
		this.enforced=enforced;
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
		this.speed=t.bulletSpeed;
		this.enforced=t.enforcedBullet;
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
            if (M.pause==true)continue;
            //0向上、1向下、2向左、3向右、4不动
            int dx[] = {0,0,-speed,speed},dy[] = {-speed,speed,0,0};
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
         if (Map.isGrass(Map.isRiver(M.map[i][j]))>0 || 
        	Map.isGrass(Map.isRiver(M.map[i+icoordinateInc[dir]][j+jcoordinateInc[dir]]))>0)
        	 dieStatusChange();//打到建筑物
         
         Tank tmp=null;
         Bullet tmpb=null;
         synchronized(M.TankLst)
         {
        	 for (Tank t:M.TankLst)
        	 {
        		 if (t.valid>=1)
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
        	 tmp.dieStatusChange(true);
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
         
         M.clear(i, j, this.enforced);
         M.clear(i+icoordinateInc[dir], j+jcoordinateInc[dir], this.enforced);
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
		if(new_x1>=0&&new_x1<=26&&new_y1>=0&&new_y1<=26 &&
				new_x2>=0&&new_x2<=25&&new_y2>=0&&new_y2<=25){
			if(M.map[new_y2][new_x2] == 2 && this.enforced==false ) return false;
															  else   return true;
		}
		return false;
	}
	
	void dieStatusChange()
	{
		synchronized(this)
		{
			if (valid!=0&&T.isPlayer())Map.playMusic("hit");
			if (valid!=0)T.changeShootLimit();
			valid=0;
		}
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
	final static int MAXENEMYTANK=25;
	volatile int LeftTank = MAXENEMYTANK;
	volatile boolean bStop;
	volatile boolean wStop;
	volatile boolean Over,hqDestory;
	/*
	 * use pause will cause shield turn off
	 * early and AI shoot bullet quickly
	 */
	volatile boolean pause;
	volatile boolean cg=false;
	volatile boolean cgFlashMode=false;
	final static int cgFlashTime=1;
	volatile int cgFlashCnt=0;
	volatile int cgFlash=0;
	
	Vector<Bomb> bombs = new Vector<Bomb>();
	Vector <Saint> saints = new Vector<Saint>();
	Vector <Props> props = new Vector<Props>();
	Image blastImage[]=new Image[8];
	Image bornImage[]=new Image[4];
	Image propImage[]=new Image[6];
	
	volatile int playerLife=3,playerLevel=1;
	volatile int deadEnemyTank=0;
	volatile int nowLevel;
	volatile int notBornedEnemy=MAXENEMYTANK;
	volatile int playerLifeCount;
	final static int propTankNum=8;
	volatile int propTank=0;
	
	volatile boolean menuMode=false;
	
	
	final static int selectFlashTime=6;
	final static int halfSelectFlashTime=selectFlashTime/2;
	volatile int selectFlash=selectFlashTime;
	
	void clearAll() throws InterruptedException
	{
		synchronized(TankLst)
		{
			for (Tank t:TankLst)
				t.join();
			TankLst.clear();
		}
		synchronized(BulletLst)
		{
			for (Bullet b:BulletLst)
				b.join();
			BulletLst.clear();
		}
		SeaCoordinate.clear();
		synchronized(bombs)
		{
			bombs.clear();
		}
		synchronized(saints)
		{
			saints.clear();
		}
		synchronized(props)
		{
			for (Props p:props)
				p.join();
			props.clear();
		}
		LeftTank=MAXENEMYTANK;
	}
	
	class MainThread extends Thread {
		
		void playCg()
		{
			Thread sweep=new Thread(){
				public void run()
				{
					while (cgFlashMode)
					{
						try{sleep(10);}
						catch(InterruptedException e){System.out.println(e);}
					}
					
					try {clearAll();} 
					catch (InterruptedException e1) {e1.printStackTrace();}
					
					if (Over==true){TestMap.level=1;}
						else TestMap.level=(TestMap.level+1)%(TestMap.maxMapNum+1);
					
					while(loadMap(TestMap.level)==false)
						TestMap.level=(TestMap.level+1)%(TestMap.maxMapNum+1);
					
					try{
						sleep(3000);
						playMusic("start");
						sleep(2000);
					}catch(InterruptedException e){
						System.out.println(e);
					}
					
					//System.out.println(TestMap.level);
					notBornedEnemy=MAXENEMYTANK;
					propTank=0;
					deadEnemyTank=0;
					Over=false;
					bStop=false;
					hqDestory=false;
					cg=false;
				}
			};
			cg=true;
			cgFlashMode=true;
			cgFlashCnt=0;
			cgFlash=0;
			sweep.start();
			while (cg==true)
			{
				repaint();
				try{
					sleep(TestMap.freshTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
			}
		}
		
		public void run(){
			/*
			 * editor mode on
			 * Map editor 
			 */
			
			ImageIcon icon = new ImageIcon();
			for (int i=0;i<8;i++)
			{
				icon=new ImageIcon("pictures/blast"+(i+1)+".gif");
				blastImage[i]=icon.getImage();
			}//Initialize blast images
			
			for (int i=0;i<4;i++)
			{
				icon=new ImageIcon("pictures/born"+(i+1)+".gif");
				bornImage[i]=icon.getImage(); 
			}//Initialize born images
			
			for (int i=0;i<6;i++)
			{
				icon=new ImageIcon("pictures/prop"+(i+1)+".gif");
				propImage[i]=icon.getImage();
			}
			TestMap.level--;
			menuMode=true;
			while (!wStop)
			{
				boolean clearLastPlay=false;
				while (menuMode==true)
				{
					repaint();
					if (clearLastPlay==false)
					{
						try {
							clearAll();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						clearLastPlay=true;
					}
				}
				if (editorMode==true)
				{
					mapEditorInitialize();
					while (editorMode==true)
					{
						repaint();
						try{
							sleep(TestMap.freshTime);
						}catch(InterruptedException e){
							System.out.println(e);
						}
					}
					continue;
				}
				playCg();
				int new_tank_time = 4*1000,cnt = 11;
				
				NewPlayerTank(playerLife,playerLevel);
				NewEnemyTank(0,cnt++);
				NewEnemyTank(1,cnt++);
				NewEnemyTank(2,cnt++);
				
				while(! bStop)
				{
					repaint();
					if(LeftTank > 0 && new_tank_time <= 0)
					{
						new_tank_time = 4*1000;
						switch(LeftTank%3)
						{
							case 0:
									if(! Tank.overlap(cnt,10,30,TankLst,saints))
									{
											NewEnemyTank(0,cnt++);
											break;					
									}
							case 1:
									if(! Tank.overlap(cnt,490,30,TankLst,saints))
									{
											NewEnemyTank(2,cnt++);
											break;
									}
							case 2:
									if(! Tank.overlap(cnt,250,30,TankLst,saints))
									{
											NewEnemyTank(1,cnt++);
											break;
									}
							default:
								if(!Tank.overlap(cnt,10,30,TankLst,saints))
								{
										NewEnemyTank(0,cnt++);
										break;					
								}
								if(!Tank.overlap(cnt,490,30,TankLst,saints))
								{
										NewEnemyTank(2,cnt++);
										break;
								}
						}
				}
				try{
					sleep(TestMap.freshTime);
					if (pause==false)new_tank_time -= TestMap.freshTime;
				}catch(InterruptedException e){
					System.out.println(e);
				}
				if (deadEnemyTank==Map.MAXENEMYTANK)
				{
					deadEnemyTank=0;
					Thread gameClear=new Thread(){
						public void run()
						{
							try{
								sleep(6000);
							}catch(InterruptedException e){
								System.out.println(e);
							}
							bStop=true;
						}
					};
					gameClear.start();
				}
			}
				
			if (!Over)
			{
				nowLevel++;
				synchronized(TankLst)
				{
					for (Tank t:TankLst)
						if (t.valid!=0)
							if (t.isPlayer())
							{
								playerLife=t.player_life;
								playerLevel=t.tankLevel;
							}
				}
			}
			else
				{
					nowLevel=1;
					playerLife=3;
					playerLevel=1;
					TestMap.level=0;
					menuMode=true;
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
	void mapEditorInitialize()
	{
		wStop=false;
		bStop=false;
		pause=false;
		editorMode=true;
		Over=false;
		findState=0;
		editorX=0;editorY=0;
		
		for (int i=0;i<26;++i)
			for (int j=0;j<26;++j)
				map[i][j]=0;
		
		map[24][12]=map[24][13]=map[25][12]=map[25][13]=5;
		map[23][11]=map[24][11]=map[25][11]=map[23][12]=map[23][13]
				=map[23][14]=map[24][14]=map[25][14]=1;
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
		if (KeyboardCode==KeyEvent.VK_ESCAPE)
		{
			menuMode=true;
			editorMode=false;
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
		TestMap.level=-1;
	}
	
	void menuEvent(int keyboardCode)
	{
		if (keyboardCode==KeyEvent.VK_UP||
			keyboardCode==KeyEvent.VK_DOWN)
		{
			editorMode=!editorMode;
			return;
		}
		if (keyboardCode==KeyEvent.VK_ESCAPE)
		{
			wStop=true;
			bStop=true;
			editorMode=false;
			menuMode=false;
			System.exit(0);
		}
		if (keyboardCode==KeyEvent.VK_SPACE)
		{
			playerLife=3;
			playerLevel=1;
			menuMode=false;
		}
	}
	
	Map(int level){
		wStop=false;
		pause=false;
		editorMode=false;
		Over=false;
		menuMode=true;
		TestMap.level=level;
		nowLevel=level;
		//loadMap(level);
		this.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				bStop = true;
				wStop = true;
				Over=true;
				menuMode=false;
				System.exit(0);
			}
		});
		/*
		 * keyboard listener
		 */
		this.addKeyListener(new KeyAdapter(){
			public void keyPressed(KeyEvent e)
			{
				if (menuMode==true)
				{
					menuEvent(e.getKeyCode());
					return;
				}
				if (editorMode==true)
				{
					editorEvent(e.getKeyCode());
					return;
				}
				if (!Over)
				{
					if (e.getKeyCode()==KeyEvent.VK_ESCAPE)
					{
						menuMode=true;
						Over=true;
						TestMap.level=0;
						bStop=true;
						return;
					}
					if (e.getKeyCode()==KeyEvent.VK_P)
					{
						pause=!pause;
						return;
					}
					if (pause==true)return;
					synchronized(TankLst)
					{
						for (Tank tank:TankLst)
						{
							if (tank.isPlayer())
								tank.player_move(e.getKeyCode());
						}
					}
				}
			}
			public void keyReleased(KeyEvent e)
			{
				if (!Over)
				{
					if (pause)return;
					synchronized(TankLst)
					{
						for (Tank tank:TankLst)
						{
							if (tank.isPlayer())
								tank.player_stopMove(e.getKeyCode());
						}
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
	
	boolean loadMap(int level)
	{
		try{
			File f = new File("Maps");
			File fs[] = f.listFiles();
			File mission=null;
			for (File F : fs){
				if (F.getName().equals("Map"+level+".txt"))
					mission=F;
			}
			if (mission==null)return false;
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
		return true;
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
		g.drawImage(images, 250, 510, 40, 40,this);
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
	
	final static int seaStreamPeriod=200;
	int seaStreamState=seaStreamPeriod*2;
	
	Image imageBuffer=null;
	Graphics graImage=null;
	
	public void update(Graphics g)
	{
		imageBuffer=createImage(this.getWidth(),this.getHeight());
		graImage=imageBuffer.getGraphics();
		paint(graImage);
		graImage.dispose();
		g.drawImage(imageBuffer, 0, 0, this);
	}
	
	public void paint(Graphics g){
		super.paint(g);
		g.setColor(Color.white);
		g.drawLine(10, 30, 530, 30);
		g.drawLine(10, 30, 10, 550);
		g.drawLine(530, 30, 530, 550);
		g.drawLine(10, 550, 530, 550);
		if (menuMode==true)
		{
			ImageIcon icon=new ImageIcon("pictures/battlecity.png");
			Image image=icon.getImage();
			g.drawImage(image, 50, 70, 440, 160,this);
			icon=new ImageIcon("pictures/game.png");
			image=icon.getImage();
			g.drawImage(image,210 , 310,120 ,20, this);
			icon=new ImageIcon("pictures/mapeditor.png");
			image=icon.getImage();
			g.drawImage(image, 210, 370,120,25 ,this);
			if (selectFlash>=halfSelectFlashTime)
				icon=new ImageIcon("pictures/select.png");
			else
				icon=new ImageIcon("pictures/selectB.png");
			selectFlash--;
			if (selectFlash<0)selectFlash=selectFlashTime;
			image=icon.getImage();
			if (editorMode==true)
				g.drawImage(image, 150, 360, 30,35,this);
			else
				g.drawImage(image, 150, 300, 30,35,this);
			return;
		}
		if (editorMode==true)
		{
			editorPaint(g);
			return;
		}
		String path = "pictures";
		if (cg==true)
		{
			String dir =path+"/"+"bghuise.png";
			ImageIcon icon = new ImageIcon(dir);
			Image images = icon.getImage();
			for (int i=0;i<26;++i)
				for (int j=0;j<26;++j)
					if (i<=cgFlash||25-i<=cgFlash)
					g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
			cgFlashCnt++;
			if (cgFlashMode&&cgFlashCnt==cgFlashTime)
			{
				cgFlashCnt=0;
				cgFlash++;
				if (cgFlash==14)
					cgFlashMode=false;
			}
			if (cgFlashMode)return;
			g.setFont(new Font("Lithos",Font.BOLD,24));
			g.setColor(Color.BLACK);
			g.drawString("STAGE "+nowLevel, 210, 270);
			return;
		}
		ImageIcon bg=new ImageIcon("pictures/bghuise.png");
		Image bgimg=bg.getImage();
		g.drawImage(bgimg, 530, 30, 130,530,this);
		for (Path Sea : SeaCoordinate)
		{
			String dir;
			if (this.seaStreamState>=seaStreamPeriod)
			{
				seaStreamState--;
				dir=path+"/3.gif";
			}
			else 
			{
				seaStreamState--;
				if (seaStreamState==0)seaStreamState=seaStreamPeriod*2;
				dir=path+"/3B.gif";
			}
			int i=Sea.x,j=Sea.y;
			ImageIcon icon=new ImageIcon(dir);
			Image images=icon.getImage();
			g.drawImage(images, 10+j*20, 30+i*20, 20,20,this);
		}
		paintTank(g);
		paintBullet(g);
		paintBlast(g);
		paintSaint(g);
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++){
				if(map[i][j] != 0 && map[i][j] != 5 &&map[i][j] !=3){
					if (this.steelFlashMode==true)
					{
						if (Map.inHQ(i, j))
						{
							String dir;
							if (this.steelFlash>=this.halfSteelFlashTime)
								dir=path+"/2.gif";
							else
								dir=path+"/1.gif";
							ImageIcon icon = new ImageIcon(dir);
							Image images = icon.getImage();
							g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
						}
						else
						{
							String dir = path + "/" + map[i][j] + ".gif";
							ImageIcon icon = new ImageIcon(dir);
							Image images = icon.getImage();
							g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
						}
					}
					else
					{
						String dir = path + "/" + map[i][j] + ".gif";
						ImageIcon icon = new ImageIcon(dir);
						Image images = icon.getImage();
						g.drawImage(images, 10+j*20, 30+i*20, 20, 20,this);
					}
				}
			}
		}
		if (this.steelFlashMode==true)
		{
			this.steelFlash--;
			if (this.steelFlash==0)
			{
				this.steelPeriod++;
				this.steelFlash=Map.steelFlashTime;
			}
		}
		if (!Over)
		{
			String dir = path + "/symbol.gif";
			ImageIcon icon = new ImageIcon(dir);
			Image images = icon.getImage();
			g.drawImage(images, 250, 510 , 40, 40,this);
			if (pause==true)
			{
				dir=path+"/pause.png";
				icon=new ImageIcon(dir);
				images=icon.getImage();
				g.drawImage(images, 230, 250, 70,20,this);
			}
		}
		else
		{
			String dir;
			ImageIcon icon;
			Image images;
			if (hqDestory)
			{
				dir=path+"/destory.gif";
				icon=new ImageIcon(dir);
				images=icon.getImage();
				g.drawImage(images, 250, 510, 40,40,this);
			}
			else
			{
				dir=path+"/symbol.gif";
				icon=new ImageIcon(dir);
				images=icon.getImage();
				g.drawImage(images, 250, 510, 40,40,this);
			}
			dir=path+"/over.gif";
			icon=new ImageIcon(dir);
			images =icon.getImage();
			g.drawImage(images, 150, 250, 250, 60,this);
		}
		paintProp(g);
		paintEnemy(g);
		paintPlayerLife(g);
		paintMapState(g);
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
	        if (pause==false)b.lifeDown();
	        //如果life=0，将炸弹从bombs向量去掉
	        if(b.isLive==false) bombs.remove(b);
	    }
	}
	
	void paintSaint(Graphics g)
	{
		Saint sa[]=new Saint[MAXENEMYTANK];
		int cnt=0;
		synchronized(saints)
		{
			for (int i=0;i<saints.size();i++)
			{
				Saint s=saints.get(i);
				g.drawImage(bornImage[s.life-1], s.x, s.y, 40, 40, this);
				if (pause==false)s.lifeDown();
				if (s.isLive==false) sa[cnt++]=s;
			}
			for (int i=0;i<cnt;++i)saints.removeElement(sa[i]);
		}
	}
	
	void paintProp(Graphics g)
	{
		synchronized(props)
		{
			for (Props p : props)
			{
				if (p.valid==false )continue;
				if (p.propFlash>=p.halfPropFlashTime)
					g.drawImage(propImage[p.number], p.y, p.x, 40,40,this);
				if (pause==false)p.flashDown();
			}
		}
	}
	
	
	//650,560 10~530 30~550
	void paintEnemy(Graphics g)
	{
		ImageIcon icon=new ImageIcon("pictures/enemy.png");
		Image image=icon.getImage();
		int x=50,y=570;
		for (int i=0;i<notBornedEnemy;++i)
		{
			g.drawImage(image, y, x, 20,20,this);
			if (y==570)y+=20;
					else {y=570;x+=20;}
		}
	}
	
	void paintPlayerLife(Graphics g)
	{
		ImageIcon icon=new ImageIcon("pictures/1p.png");
		Image image=icon.getImage();
		g.drawImage(image, 570, 370, 40,20,this);
		icon=new ImageIcon("pictures/player.png");
		image=icon.getImage();
		g.drawImage(image, 570, 395, 20,20,this);
		g.setFont(new Font("Lithos",Font.BOLD,24));
		g.setColor(Color.BLACK);
		g.drawString(""+this.playerLifeCount, 600, 415);
	}
	
	void paintMapState(Graphics g)
	{
		ImageIcon icon=new ImageIcon("pictures/flag.png");
		Image image=icon.getImage();
		g.drawImage(image, 570, 450, 40, 40,this);
		g.setFont(new Font("Lithos",Font.BOLD,20));
		g.setColor(Color.BLACK);
		g.drawString(""+TestMap.level, 580, 515);
	}
	
	//used for painting special flash tank
	final static int id10texture[][]={{0,0},{0,0},{1,2},{0,1},{0,2}};
	final static int id12texture[][]={{0,0},{0,0},{1,2},{0,1},{0,3}};
	void paintTank(Graphics g)
	{
		String path = "pictures";
		synchronized(TankLst)
		{
			for(Tank t : TankLst){
				if(t.valid == 0) continue;
				if(t.isPlayer())
				{
					String level="";
					switch(t.tankLevel){
						case 2:level="";break;
						case 3:level="B";break;
						case 4:level="C";break;
					}
					String dir = path + "/" + t.id + t.dir + level+".gif";
					ImageIcon icon = new ImageIcon(dir);
					Image images = icon.getImage();
					g.drawImage(images,t.x, t.y,40, 40, this);
					if (t.isShieldOn())
					{
						if (t.shieldFlash>=Tank.halfShieldFlashTime)
							dir=path+"/"+"shield0.gif";
						else
							dir=path+"/"+"shield1.gif";
						if (pause==false)t.shieldDown();
						icon=new ImageIcon(dir);
						images=icon.getImage();
						g.drawImage(images, t.x, t.y, 40,40,this);
					}
					continue;
				}
				if(t.id==10||t.id==12)
				{
					String dir;
					if (t.tankFlash>Tank.halftankFlashTime)
					{
						if (t.id==10)
							dir=path+"/"+t.id+id10texture[t.valid][0]+t.dir+".gif";
						else
							dir=path+"/"+(t.id-2)+id12texture[t.valid][0]+t.dir+".gif";
					}
					else
					{
						if (t.id==10)
							dir=path+"/"+t.id+id10texture[t.valid][1]+t.dir+".gif";
						else
							dir=path+"/"+(t.id-2)+id12texture[t.valid][1]+t.dir+".gif";
					}
					if (pause==false)t.tankFlashDown();
					ImageIcon icon=new ImageIcon(dir);
					Image images=icon.getImage();
					g.drawImage(images, t.x, t.y,40,40,this);
					continue;
				}
				if (t.id==11||t.id==13)
				{
					String dir;
					if (t.tankFlash>Tank.halftankFlashTime)
						dir=path+"/"+t.id+"0"+t.dir+".gif";
					else
					{
						if (t.id==13)
							dir=path+"/"+(t.id-4)+t.dir+".gif";
						else
							dir=path+"/"+t.id+"1"+t.dir+".gif";
					}
					if (pause==false)t.tankFlashDown();
					ImageIcon icon=new ImageIcon(dir);
					Image images=icon.getImage();
					g.drawImage(images, t.x, t.y,40,40,this);
					continue;
				}
				if (t.id==14)
				{
					String dir;
					if (t.tankFlash>Tank.halftankFlashTime)
						dir=path+"/"+t.id+t.dir+".png";
					else
						dir=path+"/"+(t.id-6)+t.dir+".gif";
					if (pause==false)t.tankFlashDown();
					ImageIcon icon=new ImageIcon(dir);
					Image images=icon.getImage();
					g.drawImage(images, t.x, t.y,40,40,this);
					continue;
				}
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
	
	/*
	 *  isXXX method
	 *  if the number n is XXX's code
	 *  (note : brick is 1,steel is 2,
	 *  		sea is 3,grass is 4)
	 *  then return 0
	 *  else return n
	 */
	final static int isGrass(int n)
	{
		if (n==4)return 0;
			else return n; 
	}
	
	final static int isRiver(int n)
	{
		if (n==3)return 0;
			else return n; 
	}
	
	final static int isBrick(int n)
	{
		if (n==1)return 0;
			else return n; 
	}
	
	final static int isSteel(int n)
	{
		if (n==2)return 0;
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
	
	final static void shuffle(int []a,int len,int num)
	{
		int tmp;
		for (int i=0;i<num;++i)
		{
			int j=(int)(Math.random()*len);
			int k=(int)(Math.random()*len);
			tmp=a[j];
			a[j]=a[k];
			a[k]=tmp;
		}
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
		int ind=(int)(Math.random()*300)%3;
		int tx=Map.hqEndx[ind],ty=Map.hqEndy[ind];
		int dirs[]={0,1,2,3};
		while (!que.isEmpty())
		{
			Path u=que.poll();
			int ux=u.x,uy=u.y;
			shuffle(dirs,4,10);
			for (int dir=0;dir<4;++dir)
			{
				int vx=ux+dx[dirs[dir]];
				int vy=uy+dy[dirs[dir]];
				if (!out(vx,vy)&&canGoTo(vx,vy)&&vis[vx][vy]==0)
				{
					pre[vx][vy]=new Path(ux,uy);
					vis[vx][vy]=1;
					if (vx==tx&&vy==ty)
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
	final static boolean out(int x,int y)
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
		if (isBrick(isGrass(map[y][x]))==0&&isBrick(isGrass(map[y][x+1]))==0&&
				isBrick(isGrass(map[y+1][x]))==0&&isBrick(isGrass(map[y+1][x+1]))==0)return true;
		return false;
	}
	
	/*
	 * map[x][y] is close to the HQ?
	 */
	final static int[] hqEndx=new int[]{9,15,12};
	final static int[] hqEndy=new int[]{24,24,21};
	final static boolean nearHQ(int x,int y)
	{
		for (int i=0;i<3;++i)
			if (x==hqEndx[i]&&y==hqEndy[i])
				return true;
		return false;
	}
	
	final static boolean canDropProp(int x,int y,Map M)
	{
		if (Map.inHQ(x, y))return false;
		synchronized(M.TankLst)
		{
			for (Tank t: M.TankLst)
				if (t.isPlayer())
					if (Math.abs(y*20+10-t.x)<40&&Math.abs(x*20+30-t.y)<40)
						return false;
		}
		int data=isSteel(isRiver(M.map[x][y+1]))|isSteel(isRiver(M.map[x+1][y]))|
				isSteel(isRiver(M.map[x][y+1]))|isSteel(isRiver(M.map[x+1][y+1]));
		if (data>0)return true;
		return false;
	}
	
	final static int attackHQ(int x,int y)
	{
		if (x==9&&y==24)return 3;
		if (x==15&&y==24)return 2;
		if (y==21&&x==12)return 1;
		return 0;
	}

	void newTank(int x, int y, int id, int dir, Map M, int num, int speed, int player_life)
	{
		Tank new_tank = new Tank(x,y,id,dir,this,num,speed,player_life);//speed=4
		if (bStop)return;
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
		if (bStop)return;
		synchronized(TankLst)
		{
			TankLst.add(new_tank);
		}
		Thread new_th = new Thread(new_tank);
		new_th.start();
		LeftTank--;
	}
	
	void newTank(int player_life,int level)
	{
		//NewTank(190,510,6,0,Map.this,cnt++,8,3);
		Tank new_tank=new Tank(170,510,6,0,this,10,8,player_life,level);
		if (bStop)return;
		synchronized(TankLst)
		{
			TankLst.add(new_tank);
		}
		Thread new_th=new Thread(new_tank);
		new_th.start();
	}

	void NewPlayerTank(int player_life,int level)
	{
		Saint s=new Saint(170,510);
		synchronized(saints)
		{
			saints.add(s);
		}
		Thread new_t=new Thread(){
			public void run()
			{
				while (!bStop&&s.isLive==true);
				newTank(player_life,level);
			}
		};
		new_t.start();
	}
	
	void NewEnemyTank(int x,int y,int id,int dir,int num,int speed,int player_life)
	{
		NewTank(x,y,id,dir,this,num,speed,player_life);
		LeftTank--;
	}
	
	//10,250,490,30
	final static int enemyPosition[]=new int[]{10,250,490};
	final static int enemySpeed[]=new int[]{0,0,0,0,0,0,0,4,4,10,4,4,4,10,4};
	void NewEnemyTank(int positionId,int id,int num)
	{
		if (Tank.isPropTank(id))propTank++;
		int x=enemyPosition[positionId],y=30;
		NewEnemyTank(x,y,id,1,num,enemySpeed[id],3);
	}
	
	void NewEnemyTank(int positionId,int num)
	{
		if (propTank<propTankNum)
		{
			int x=(int)(Math.random()*8);
			x+=7;if (x==15)x--;
			NewEnemyTank(positionId,x,num);
		}
		else
		{
			int x=(int)(Math.random()*4);
			x+=7;if (x==11)x--;
			NewEnemyTank(positionId,x,num);
		}
	}
	
	void NewTank(int x,int y,int cnt)
	{
		class NewTank1 extends Thread
		{
			int x,y,cnt;
			Saint s;
			NewTank1(int x,int y,int cnt,Saint s)
			{
				this.x=x;this.y=y;this.cnt=cnt;
				this.s=s;
			}
			public void run()
			{
				while (!bStop&&s.isLive==true);
				newTank(x,y,cnt);
			}
		}
		Saint s=new Saint(x,y);
		synchronized(saints)
		{
			saints.add(s);
		}
		NewTank1 new_n=new NewTank1(x,y,cnt,s);
		Thread new_t=new Thread(new_n);
		new_t.start();
		notBornedEnemy--;
	}
	
	void NewTank(int x, int y, int id, int dir, Map M, int num, int speed, int player_life)
	{
		class NewTank1 extends Thread
		{
			int x,y,id,dir,num,speed,player_life;
			Map M;
			Saint s;
			NewTank1(int x, int y, int id, int dir, Map M, int num, int speed, int player_life,Saint s)
			{
				this.x=x;this.y=y;this.id=id;this.dir=dir;
				this.M=M;this.num=num;this.speed=speed;
				this.player_life=player_life;this.s=s;
			}
			public void run()
			{
				while (!bStop&&s.isLive==true);
				newTank(x,y,id,dir,M,num,speed,player_life);
			}
		}
		Saint s=new Saint(x,y);
		synchronized(saints)
		{
			saints.add(s);
		}
		NewTank1 new_n=new NewTank1(x,y,id,dir,M,num,speed,player_life,s);
		Thread new_t=new Thread(new_n);
		new_t.start();
		notBornedEnemy--;
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
	
	void clear(int i,int j,boolean enforcedBullet)
	 {
		i=check(i);
		j=check(j);
		if(map[i][j] == 1) map[i][j] = 0;
			else 
				if(map[i][j] == 2) 
				{
					if (enforcedBullet==false)map[i][j] = 2;
										else  map[i][j] = 0; 
				}
				else if(map[i][j] == 5) 
				{
					if (!Over)Map.playMusic("blast");
					gameOver(true);
				}
	 }
	
	void gameOver(boolean destory)
	{
		class GameOverTimeCount extends Thread
		{
			Map M;
			GameOverTimeCount(){M=null;}
			GameOverTimeCount(Map M){this.M=M;}
			
			final static int CountTime=5000;
			public void run()
			{
				try
				{
					sleep(CountTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				M.bStop=true;
			}
		}
		playerLife=3;
		playerLevel=1;
		if (Over)
		{
			hqDestory=destory|hqDestory;
			return;
		}
		Over=true;
		hqDestory=destory;
		GameOverTimeCount count=new GameOverTimeCount(this);
		Thread new_t=new Thread(count);
		new_t.start();
		Map.playMusic("lose");
	}
	
	final static void playStartMusic(String path)
	{
		try{
			FileInputStream fileau=new FileInputStream("music/"+path+".wav");
			AudioStream as=new AudioStream(fileau);
			AudioPlayer.player.start(as);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/*
	 * wrapping method for playing music
	 * playing music named "Path.wav" in documents
	 * "music" , Path is the name of the music file
	 */
	final static void playMusic(String Path)
	{
		MusicPlayer mp=new MusicPlayer(Path);
		Thread new_t=new Thread(mp);
		new_t.start();
	}
	
	final static int steelGuardTime=12000;
	final static int steelFlashTime=8;
	final static int halfSteelFlashTime=steelFlashTime/2;
	final static int steelPeriodTime=5;
	volatile boolean steelFlashMode=false;
	int steelFlash=steelFlashTime;
	volatile int steelPeriod=0;
	
	final static int hqPosx[]=new int[]{23,24,25,23,23,23,24,25};
	final static int hqPosy[]=new int[]{11,11,11,12,13,14,14,14};
	
	final static boolean inHQ(int x,int y)
	{
		for (int i=0;i<8;++i)
			if (hqPosx[i]==x&&hqPosy[i]==y)
				return true;
		return false;
	}
	
	/*
	 * used for steel guard prop,using this prop will make 
	 * our HQ surrounded with steel block
	 */
	final void steelGuard()
	{
		class steelGuardControl extends Thread{
			steelGuardControl(){}
			public void run()
			{
				steelFlashMode=false;
				steelPeriod=0;
				try{
					sleep(steelGuardTime);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				steelFlashMode=true;
				while (!bStop&&steelPeriod<=steelPeriodTime)
				{
					try{
						sleep(10);
					}catch(InterruptedException e){
						System.out.println(e);
					}
				}
				if (steelFlashMode==true)
				{
					map[23][11]=map[24][11]=map[25][11]=map[23][12]=map[23][13]
							=map[23][14]=map[24][14]=map[25][14]=1;
				}
				steelFlashMode=false;
			}
		}
		map[23][11]=map[24][11]=map[25][11]=map[23][12]=map[23][13]
				=map[23][14]=map[24][14]=map[25][14]=2;
		steelGuardControl sgc=new steelGuardControl();
		Thread new_t=new Thread(sgc);
		new_t.start();
	}
	
	/*
	 * used for bomb prop,use this prop will kill all the enemy
	 * tank on the map
	 */
	final void killAllEnemyTank()
	{
		Tank []enemy_tank=new Tank[Map.MAXENEMYTANK];
		int tot=0;
		synchronized(TankLst)
		{
			for (Tank t : TankLst)
				if (t.valid>=1)
				{
					if (t.isPlayer());
						else enemy_tank[tot++]=t;
				}
		}
		for (int i=0;i<tot;++i)
			enemy_tank[i].dieStatusChange(false);
	}
}

public class TestMap {
	volatile static int level=1;//determine the level to play first
	final static int maxMapNum=16;
	final static int freshTime=25;
	public static void main(String[] args) {
			Map M = new Map(level);
	}
}
