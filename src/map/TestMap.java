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

class Tank extends Thread {
	/*
	 * valid: the tank is dead? 1=not dead
	 * 
	 * x,y: position x 10~530 y 30~550
	 * 
	 * id: which tank texture to use
	 * 
	 * dir: the direction of the tank
	 * 
	 * num: the number code of the tank
	 * the player tank is 10
	 * and other tank's number > 10
	 * 
	 * speed: the moving speed of the tank
	 * 
	 * randomStep: the AI random opeartation times at the beginning 
	 * after the randomStep
	 * then the AI will select the shortest path to the HQ
	 * 
	 * nowStep:record the AI's next target position to arrive 
	 * 
	 * shootLimit: judge whether the tank can shoot (1 shoot/5 seconds) 
	 * 
	 * M:the map M
	 * 
	 * 
	 */
	volatile int valid, x, y, dir;
	int id, num, speed;
	final static int MAXSTEP=200;
	int randomStep=MAXSTEP;
	int nowStep=-1;
	volatile boolean shootLimit=false;
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
	
	Tank(int x,int y,int id,int dir,Map M,int num,int speed)
	{
		valid = 1;
		this.x = x;
		this.y = y;
		this.id = id;
		this.dir = dir;
		this.M = M;
		this.num = num;
		this.speed = speed;
		dx=new int[]{0,0,-speed,speed};
		dy=new int[]{-speed,speed,0,0};
		this.shootLimit=false;
	}
	
	public void run() 
	{
		if (num>10)
		{
			while((! M.bStop)&&(this.valid>=1))
			{
				try{
					sleep(TestMap.freshTime*5);
				}catch(InterruptedException e){
					System.out.println(e);
				}
				ai_move();
			}
			return;
		}
		else
		{
			while ((! M.bStop)&&(this.valid>=1));
			System.out.println("End!");
			return;
		}
	}
	/*
	 * keyBoard Code to Number
	 */
	int keyCodeToNum(int keyboardCode)
	{
		if (keyboardCode==KeyEvent.VK_UP)return 0;
		if (keyboardCode==KeyEvent.VK_DOWN)return 1;
		if (keyboardCode==KeyEvent.VK_LEFT)return 2;
		if (keyboardCode==KeyEvent.VK_RIGHT)return 3;
		if (keyboardCode==KeyEvent.VK_SPACE)return -2;
		return -1;
	}
	
	/*
	 * player_move
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
		/*
		 * changeState:
		 * synchronized function to
		 * change the tank's x,y,dir
		 * 
		 * dir_tmp:the next move
		 */
		changeState(dir_tmp);
	}
	
	int smoothPos(int x,int y,int lastDir,int dir)
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
	boolean out(int x,int y)
	{
		if (x<10||x>490)return true;
		if (y<30||y>510)return true;
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
					overlap(num,x,y) == false)
				return true;
		}
		return false;
	}
	
	/*
	 * judge whether two tank is not overlapped
	 */
	boolean overlap(int t,int x,int y)
	{
		synchronized(M.TankLst)
		{
			for(Tank tank : M.TankLst){
				if(tank.valid==1)
					if(tank.num != t && (Math.abs(tank.x-x)<45 && Math.abs(tank.y-y)<45))
						return true;
			}
			return false;
		}
	}
	
	/*
	 * used for AI find the next move direction
	 * when AI already knows the shortest path from
	 * her to the HQ
	 */
	int whereToGo(int x,int y,int Targetx,int Targety)
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
	
	void dieStatusChange()
	{
		valid=0;
		M.deleteTank(this);
	}
	
}

class Bullet extends Thread {
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
            int dx[] = {0,0,-8,8},dy[] = {-8,8,0,0};
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
            	else
            		move--;
            
            changeState();
            
            /*if (dir==0)//向上
            {
                int j=(int)Math.floor(1.0*(x-2-10)/20.0);
                int i=(int)Math.floor(1.0*(y-30)/20.0);
                i=check(i);j=check(j);
                if (M.map[i][j]>0 || M.map[i][j+1]>0) dieStatusChange();//打到建筑物
                
                for (Tank t:M.TankLst)
                {
                    if (t.valid==1)
                    {
                        if (x+3>=t.x && t.x+40>=x-3 && y+3>=t.y && t.y+40>=y-3)
                        {
                            if ((flag==0 && t.num>10) || (flag==1 && t.num==10))
                            {
                                t.dieStatusChange();
                                dieStatusChange();
                            }
                        }
                    }
                }
                M.clear(i, j);
                M.clear(i, j+1);
            }
            if (dir==1)
            {
                int j=(int)Math.floor(1.0*(x-2-10)/20.0);
                int i=(int)Math.floor(1.0*(y-30)/20.0);
                i=check(i);j=check(j);
                if (M.map[i][j]>0 || M.map[i][j+1]>0) dieStatusChange();//打到建筑物
                
                for (Tank t:M.TankLst)
                {
                    if (t.valid==1)
                    {
                        if (x+3>=t.x && t.x+40>=x-3 && y+3>=t.y && t.y+40>=y-3)
                        {
                            if ((flag==0 && t.num>10) || (flag==1 && t.num==10))
                            {
                                t.dieStatusChange();
                                dieStatusChange();
                            }
                        }
                    }
                }
                
                M.clear(i, j);
                M.clear(i, j+1);
            }
            
            if (dir==2)
            {
                int j=(int)Math.floor(1.0*(x-10)/20.0);
                int i=(int)Math.floor(1.0*(y-2-30)/20.0);
                i=check(i);j=check(j);
                if (M.map[i][j]>0 || M.map[i+1][j]>0) dieStatusChange();//打到建筑物
                
                for (Tank t:M.TankLst)
                {
                    if (t.valid==1)
                    {
                        if (x+3>=t.x && t.x+40>=x-3 && y+3>=t.y && t.y+40>=y-3)
                        {
                            if ((flag==0 && t.num>10) || (flag==1 && t.num==10))
                            {
                                t.dieStatusChange();
                                dieStatusChange();
                            }
                        }
                    }
                }
                
                M.clear(i, j);
                M.clear(i+1, j);
            }
            
            if (dir==3)
            {
                int j=(int)Math.floor(1.0*(x-10)/20.0);
                int i=(int)Math.floor(1.0*(y-2-30)/20.0);
                i=check(i);j=check(j);
                if (M.map[i][j]>0 || M.map[i+1][j]>0)dieStatusChange();//打到建筑物
                
                for (Tank t:M.TankLst)
                {
                    if (t.valid==1)
                    {
                        if (x+3>=t.x && t.x+40>=x-3 && y+3>=t.y && t.y+40>=y-3)
                        {
                            if ((flag==0 && t.num>10) || (flag==1 && t.num==10))
                            {
                                t.dieStatusChange();
                                dieStatusChange();
                            }
                        }
                    }
                }
                M.clear(i, j);
                M.clear(i+1, j);
            }
            //判断是否打到任何东西
            if (x<10 || y<30 || x>530 || y>550)dieStatusChange();*/
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
         if (M.map[i][j]>0 || M.map[i+icoordinateInc[dir]][j+jcoordinateInc[dir]]>0)
        	 dieStatusChange();//打到建筑物
         
         Tank tmp=null;
         
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
	
	int getMapInfo(int x,int y){
		int new_x1,new_y1,new_x2,new_y2;
		new_x1 = (int)Math.ceil(1.0*(x-10)/20);
		new_x2 = (int)Math.floor(1.0*(x-10)/20);
		new_y1 = (int)Math.ceil(1.0*(y-30)/20);
		new_y2 = (int)Math.floor(1.0*(y-30)/20);
		if(new_x1>=0&&new_x1+1<26&&new_y1>=0&&new_y1+1<26 &&
				new_x2>=0&&new_x2+1<26&&new_y2>=0&&new_y2+1<26){
			if(M.map[new_y2][new_x2] == 2) return 2;//铁块
			if(M.map[new_y2][new_x2] == 1) return 1;//木块
			if(M.map[new_y2][new_x2] == 0)//底部是空地
			{
				if (new_y2>=24 && new_x2>=12 && new_x2<=13) return 3;//Home
				else
				{
					synchronized(M.TankLst)
					{
						for(Tank t : M.TankLst){ //[t.x,t.x+40]  [t.y,t.y+40]
							if (x+2>=t.x && x-2<=t.x+40 && y+2>=t.y && y-2<=t.y+40) return t.num;//此处坦克
						}
					}
				}
				return 0;
			}
		}
		return 6;
	}
	
	int getBulletInfo(int x,int y)
	{
		int a[]={0,0,0,0};
		int dx[]={-11,11,11,-11};
		int dy[]={-11,-11,11,11};
		for (int i=0;i<4;i++)
		{
			a[i]=getMapInfo(x+dx[i],y+dy[i]);
		}
		int res=a[0]*1+a[1]*100+a[2]*10000+a[3]*1000000;
		return res;
	}
	
	void dieStatusChange()
	{
		valid=0;
		T.shootLimit=false;
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
	MainThread thread;
	volatile int LeftTank = 10;
	volatile boolean bStop;
	
	class MainThread extends Thread {
		public void run(){
			int new_tank_time = 5*1000,cnt = 10;
			Tank my_tank = new Tank(9*20+10,24*20+30,6,0,Map.this,cnt++,5);
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
					if(! overlap(10,30)){
						newTank(10,30,cnt);
						cnt++;
						break out;					
					}
					if(! overlap(24*20+10,30)){
						newTank(24*20+10,30,cnt);
						cnt++;
						break out;
					}
					if(! overlap(12*20+10,30)){
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
	Map(){}
	Map(int level){
		try{
			File f = new File("Maps");
			File fs[] = f.listFiles();
			FileReader in = new FileReader(fs[level - 1]);
			BufferedReader rd = new BufferedReader(in);
			for(int i=0;i<26;i++){
				String str[] = rd.readLine().split(" ");
				for(int j=0;j<26;j++){
					map[i][j] = Integer.parseInt(str[j]);
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
		});
		this.setSize(650, 560);
		this.setBackground(Color.black);
		this.setVisible(true);
		thread = new MainThread();
		thread.start();
	}
	
	public void paint(Graphics g){
		super.paint(g);
		g.setColor(Color.white);
		g.drawLine(10, 30, 530, 30);
		g.drawLine(10, 30, 10, 550);
		g.drawLine(530, 30, 530, 550);
		g.drawLine(10, 550, 530, 550);
		String path = "pictures";
		for(int i=0;i<26;i++){
			for(int j=0;j<26;j++){
				if(map[i][j] == 1 || map[i][j] == 2){
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
		paintTank(g);
		paintBullet(g);
		//print();
	}
	void paintTank(Graphics g){
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
		//System.out.println();
	}

	void paintBullet(Graphics g){
		String path = "pictures";
		synchronized(BulletLst)
		{
			for(Bullet b : BulletLst){
				if (b.valid == 0) continue;
				String dir = path + "/" + "bullet.jpeg";
				ImageIcon icon = new ImageIcon(dir);
				Image images = icon.getImage();
				g.drawImage(images,b.x, b.y, 5, 5, this);
			}
		}
	}
	
	synchronized int getMapNum(int i,int j){
		return map[i][j]+map[i+1][j]+map[i][j+1]+map[i+1][j+1];
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
		if (map[y][x]==0&&map[y][x+1]==0&&map[y+1][x]==0&&map[y+1][x+1]==0)return true;
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
	}
	
	void newTank(int x,int y, int cnt)
	{
		Tank new_tank = new Tank(x,y,7,1,this,cnt,5);
		synchronized(TankLst)
		{
			TankLst.add(new_tank);
		}
		Thread new_th = new Thread(new_tank);
		new_th.start();
		LeftTank--;
	}
	
	void deleteTank(Tank t)
	{
		synchronized(TankLst)
		{
			TankLst.removeElement(t);
		}
	}
	
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
	final static int freshTime=50;
	public static void main(String[] args) {
		Map M = new Map(1);
	}
}
