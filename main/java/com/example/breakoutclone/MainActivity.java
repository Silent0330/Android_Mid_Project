package com.example.breakoutclone;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {

    DrawView drawView;
    Game game;
    boolean gameStart;

    public SoundPool soundPool;
    private AudioManager audioManager;
    private static final int MAX_STREAMS = 5;
    private static final int streamType = AudioManager.STREAM_MUSIC;
    private float volume;
    private boolean loaded;

    int soundId_ballJump;
    int soundId_brickHited, soundId_brickBroken;
    int soundId_itemBigBall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameStart = false;
        drawView = new DrawView(this);
        setContentView(drawView);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float currentVolumeIndex = (float) audioManager.getStreamVolume(streamType);
        float maxVolumeIndex = (float) audioManager.getStreamMaxVolume(streamType);
        this.volume = currentVolumeIndex / maxVolumeIndex;
        this.setVolumeControlStream(streamType);

        if (Build.VERSION.SDK_INT >= 21 ) {
            AudioAttributes audioAttrib = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            SoundPool.Builder builder= new SoundPool.Builder();
            builder.setAudioAttributes(audioAttrib).setMaxStreams(MAX_STREAMS);
            this.soundPool = builder.build();
        }else{
            this.soundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, 0);
        }

        this.soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                loaded = true;
            }
        });

        soundId_ballJump = soundPool.load(drawView.getContext(), R.raw.music_jump,1);
        soundId_brickHited = soundPool.load(drawView.getContext(), R.raw.music_brick_hited,1);
        soundId_brickBroken = soundPool.load(drawView.getContext(), R.raw.music_brick_broken,1);
        soundId_itemBigBall = soundPool.load(drawView.getContext(), R.raw.music_big_ball,1);
        soundId_itemBigBall = soundPool.load(drawView.getContext(), R.raw.music_big_ball,1);
    }

    class Game {
        Player player;
        Vector<Ball> stickedBalls;
        Vector<Ball> balls;
        Vector<Item> items;
        Brick[][] bricks;
        Rect gameRect;
        int cols, rows, brickCols, brickRows;
        int tileWith, tileHeight;
        int brickNum, life;
        boolean control_Shoot;
        int gameState, gameMode;
        int gameLevel, downCount, bottomRow;
        int ballSizeEffectTime, ballSizeEffect;
        int score;
        //game states
        static final int STATE_PLAYING = 0;
        static final int STATE_PAUSE = 1;
        static final int STATE_GAMEOVER = 2;
        static final int STATE_GAMEWIN = 3;
        //effects
        static final int EFFECT_NORMALBALL = 0;
        static final int EFFECT_SMALLBALL = 1;
        static final int EFFECT_BIGBALL = 2;
        //game mode
        static final int MODE_NORMAL = 0;
        static final int MODE_CHALLENGE = 1;


        Game(int game_mode) {
            gameMode = game_mode;
            cols = 10; rows = 24;
            brickCols = 10;
            if(gameMode == MODE_NORMAL) {
                brickRows = 10;
            }
            else if(gameMode == MODE_CHALLENGE) {
                brickRows = 20;
            }
            gameRect = new Rect(0, 100, drawView.getWidth(), drawView.getHeight());
            tileWith = drawView.getWidth() / cols; tileHeight = (drawView.getHeight()-gameRect.top) / rows;
            stickedBalls = new Vector<Ball>();
            balls = new Vector<Ball>();
            items = new Vector<Item>();
            player = new Player(this);
            bricks = new Brick[brickRows][brickCols];
            gameLevel = 0;
            downCount = 0;
            score = 0;
            Initial();
        }

        void Initial()
        {
            stickedBalls.clear();
            balls.clear();
            items.clear();
            player.Initial();
            brickNum = brickCols * brickRows;
            life = 3;
            ballSizeEffect= EFFECT_NORMALBALL;
            ballSizeEffectTime = 0;
            if(gameMode == MODE_NORMAL) {
                for (int i = 0; i < brickRows; i++) {
                    for (int j = 0; j < brickCols; j++) {
                        double type = Math.random();
                        if (type < 0.5)
                            type = Brick.TYPE_NORMAL;
                        else if (type < 0.8)
                            type = Brick.TYPE_ITEMBRICK;
                        else
                            type = Brick.TYPE_HARDBRICK;
                        bricks[i][j] = new Brick(this, j * tileWith, gameRect.top + i * tileHeight, tileWith, tileHeight, (int) type);
                    }
                }
            }
            else if(gameMode == MODE_CHALLENGE) {
                bottomRow = 9;
                for (int i = 0; i <= bottomRow; i++) {
                    for (int j = 0; j < brickCols; j++) {
                        double type = Math.random();
                        if (type < 0.5)
                            type = Brick.TYPE_NORMAL;
                        else if (type < 0.8)
                            type = Brick.TYPE_ITEMBRICK;
                        else
                            type = Brick.TYPE_HARDBRICK;
                        bricks[i][j] = new Brick(this, j * tileWith, gameRect.top + i * tileHeight, tileWith, tileHeight, (int) type);
                    }
                }
                for (int i = bottomRow+1; i < brickRows; i++) {
                    for (int j = 0; j < brickCols; j++) {
                        bricks[i][j] = new Brick(this, j * tileWith, gameRect.top + i * tileHeight, tileWith, tileHeight, -1);
                    }
                }
            }

            control_Shoot = false;
            gameState = STATE_PLAYING;
        }

        void addEffect(int effect) {
            if(effect >= 1 && effect <= 2) {
                ballSizeEffect = effect;
                ballSizeEffectTime = 360;
            }
        }

        void cloneBalls() {
            int s = balls.size();
            for(int i = 0; i < s; i++) {
                balls.elementAt(i).Clone();
            }
        }

        void Update() {
            if(gameMode == MODE_CHALLENGE) {
                downCount++;
                if(downCount >= 1000 - gameLevel * 50) {
                    gameLevel = (int)Math.log10(score);
                    if(gameLevel > 10) gameLevel = 10;
                    for(int i = bottomRow; i >= 0; i--) {
                        boolean empty = true;
                        for (int j = 0; j < brickCols; j++) {
                            if (!bricks[i][j].broken) {
                                empty = false;
                                break;
                            }
                        }
                        if(empty) {
                            bottomRow = i-1;
                            if(bottomRow < 0) {
                                score += 10;
                                bottomRow = 0;
                            }
                        }
                        else {
                            break;
                        }
                    }
                    bottomRow++;
                    if(bottomRow >= brickRows) {
                        gameState = STATE_GAMEOVER;
                        return;
                    }
                    for(int i = bottomRow; i > 0; i--) {
                        for (int j = 0; j < brickCols; j++) {
                            bricks[i][j].CloneState(bricks[i-1][j]);
                        }
                    }
                    for (int j = 0; j < brickCols; j++) {
                        double type = Math.random();
                        if (type < 0.5)
                            type = Brick.TYPE_NORMAL;
                        else if (type < 0.8)
                            type = Brick.TYPE_ITEMBRICK;
                        else
                            type = Brick.TYPE_HARDBRICK;
                        bricks[0][j] = new Brick(this, j * tileWith, gameRect.top, tileWith, tileHeight, (int) type);
                    }
                    Log.d("bricks", bottomRow + "  " + brickRows);
                    downCount = 0;
                }
            }

            if(ballSizeEffectTime > 0) {
                ballSizeEffectTime--;
            }
            else {
                ballSizeEffect = EFFECT_NORMALBALL;
            }

            player.Update();


            for(int i = 0; i < stickedBalls.size(); i++)
            {
                stickedBalls.elementAt(i).Update();
            }

            if(control_Shoot)
            {
                for(int i = 0; i < stickedBalls.size();)
                {
                    Ball ball = stickedBalls.elementAt(i);
                    stickedBalls.remove(ball);
                    ball.Shoot(60);
                    balls.add(ball);
                }
                control_Shoot = false;
            }

            for(int i = 0; i < balls.size(); i++)
            {
                balls.elementAt(i).Update();
                if(balls.elementAt(i).dead)
                {
                    balls.remove(i);
                    i--;
                }
            }

            for(int i = 0; i < items.size(); i++)
            {
                items.elementAt(i).Update();
                if(items.elementAt(i).dead)
                {
                    items.remove(i);
                    i--;
                }
            }

            if(balls.size() + stickedBalls.size() <= 0)
            {
                if(life > 0) {
                    stickedBalls.add(new Ball(this, player.px + player.width/2, player.py - game.tileHeight));
                    life--;
                }
                else {
                    gameState = STATE_GAMEOVER;
                }
            }

            if(brickNum <= 0)
            {
                gameState = STATE_GAMEWIN;
            }
        }
    }

    class Player {
        Game game;
        int px, py, vx, dx;
        int step;
        int width, height;
        int control_targetX;

        Player(Game game) {
            this.game = game;
            Initial();
        }

        Rect GetRect() {
            return new Rect(game.player.px , game.player.py, game.player.px + game.player.width , game.player.py + game.player.height);
        }

        void IncreseWidth()
        {
            width += 10;
            if(width > game.gameRect.right - game.gameRect.left)
            {
                width = game.gameRect.right - game.gameRect.left;
            }
        }

        void DecreseWidth()
        {
            width -= 10;
            if(width < 100)
            {
                width = 100;
            }
        }

        void Initial() {
            width = game.tileWith*2;
            height = game.tileHeight/2;
            px = drawView.getWidth()/2 - width;
            py = drawView.getHeight() - height - game.tileHeight;
            vx = 0;
            step = 20;
            dx = px;
            control_targetX = px;

            game.stickedBalls.add(new Ball(game, px + width/2, py - game.tileHeight/2));
        }

        void SetControl() {
            dx = control_targetX;
            vx = dx - px;
            /*
            vx = 0;
            if(control_targetX - px > 0)
            {
                if(control_targetX - px > step)
                    vx += step;
                else
                    vx = control_targetX - px;
            }
            else if(control_targetX - px < 0)
            {
                if(control_targetX - px < -step)
                    vx -= step;
                else
                    vx = control_targetX - px;
            }
            dx = px + vx;
            */
        }

        void Move() {
            if(dx < 0)
            {
                dx = 0;
            }
            else if(dx + width > drawView.getWidth())
            {
                dx = drawView.getWidth() - width;
            }
            if(px == dx)
                vx = 0;
            else
                px = dx;
        }

        void Update() {
            SetControl();
            Move();
        }
    }

    class Ball {
        Game game;
        int px, py, vx, vy, dx, dy;
        int maxVx, maxVy;
        int r, speed;
        int damage;
        int angle;
        boolean stick, dead;
        Bitmap bitmap;


        Ball(Game game, int x, int y) {
            this.game = game;
            Initial(x, y);
        }

        Rect GetBitmapRect()
        {
            return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        Rect GetRect()
        {
            return new Rect(px-r, py-r, px + r, py + r);
        }

        void Initial(int x, int y) {
            px = x;
            py = y;
            r = game.tileHeight/2;
            vx = 0;
            vy = 0;
            speed = 40;
            dx = px;
            dy = py;
            damage = 1;
            angle = 45;
            stick = true;
            dead = false;
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ball_white);

            maxVy = game.tileHeight;
            maxVx = game.tileWith;

        }

        void Clone() {
            Ball ball = new Ball(game, px, py);
            ball.Shoot(-vy, vx);
            game.balls.add(ball);
        }

        void Shoot(int angle) {
            this.angle = angle;
            vx = (int)(speed * Math.cos((double)angle * Math.PI / 180));
            vy = -Math.abs((int)(speed * Math.sin((double)angle * Math.PI / 180)));
            if (vx > maxVx) {
                vx = maxVx;
            }
            if (vx < -maxVx) {
                vx = -maxVx;
            }
            if (vy < -maxVy) {
                vy = -maxVy;
            }
            if(vy == 0) {
                vy = -5;
            }
            stick = false;
        }

        void Shoot(int v_x, int v_y) {
            this.angle = (int)(Math.atan((double)-v_y / (double)v_x) / Math.PI * 180);
            this.vx = v_x;
            this.vy = v_y;
            if (vx > maxVx) {
                vx = maxVx;
            }
            if (vx < -maxVx) {
                vx = -maxVx;
            }
            if (vy < -maxVy) {
                vy = -maxVy;
            }
            if (vy > maxVy) {
                vy = maxVy;
            }
            if(vy == 0) {
                vy = -5;
            }
            stick = false;
        }

        void Move() {
            dx = px + vx;
            dy = py + vy;
            //boarder
            if(dx-r < game.gameRect.left)
            {
                dx = game.gameRect.left + r;
                vx = -vx;
            }
            else if(dx + r > game.gameRect.right)
            {
                dx = game.gameRect.right - r;
                vx = -vx;
            }
            if(dy-r < game.gameRect.top)
            {
                dy = game.gameRect.top + r;
                vy = -vy;
            }
            else if(dy + r > game.gameRect.bottom)
            {
                dy = game.gameRect.bottom - r;
                vy = -vy;
                dead = true;
            }

            //player
            if (vy > 0)
            {
                if(px < game.player.GetRect().left)
                {
                    int dis_x = game.player.px - dx;
                    int dis_y = game.player.py - dy;
                    if(r * r >= dis_x * dis_x + dis_y * dis_y)
                    {
                        soundPool.play(soundId_ballJump, volume, volume, 1, 0, 1f);
                        vx = - vx;
                        vy = -vy;
                    }
                }
                else if(px > game.player.GetRect().right)
                {
                    int dis_x = game.player.px + game.player.width - dx;
                    int dis_y = game.player.py + game.player.height - dy;
                    if(r * r >= dis_x * dis_x + dis_y * dis_y)
                    {
                        soundPool.play(soundId_ballJump, volume, volume, 1, 0, 1f);
                        vx = - vx;
                        vy = -vy;
                    }
                }
                else if (game.player.GetRect().contains(dx, dy + r))
                {
                    soundPool.play(soundId_ballJump, volume, volume, 1, 0, 1f);
                    dy = game.player.py - r;
                    vy = -vy;
                    if(game.player.vx > 0)
                    {
                        if(vx > -maxVx + 5) {
                            vx += 5;
                        }
                    }
                    else if(game.player.vx < 0)
                    {
                        if(vx < maxVx - 5) {
                            vx -= 5;
                        }
                    }
                }
            }

            //brick
            if(vx > 0)
            {
                int dc = (dx - game.gameRect.left + r) / game.tileWith;
                int dr = (py  - game.gameRect.top) / game.tileHeight;
                if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx + r, py))
                {
                    game.bricks[dr][dc].Hited(damage);
                    dx =  game.bricks[dr][dc].GetRect().left - r;
                    vx = -vx;
                }
                else
                {
                    dc = (dx - game.gameRect.left + r) / game.tileWith;
                    dr = (dy - game.gameRect.top) / game.tileHeight;
                    if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx + r, dy))
                    {
                        game.bricks[dr][dc].Hited(damage);
                        dx =  game.bricks[dr][dc].GetRect().left - r;
                        vx = -vx;
                    }
                }
            }
            else if(vx < 0)
            {
                int dc = (dx - game.gameRect.left -r) / game.tileWith;
                int dr = (py - game.gameRect.top) / game.tileHeight;
                if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx -r, py))
                {
                    game.bricks[dr][dc].Hited(damage);
                    dx =  game.bricks[dr][dc].GetRect().right + r;
                    vx = -vx;
                }
                else
                {
                    dc = (dx - game.gameRect.left - r) / game.tileWith;
                    dr = (dy - game.gameRect.top) / game.tileHeight;
                    if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx -r, dy))
                    {
                        game.bricks[dr][dc].Hited(damage);
                        dx =  game.bricks[dr][dc].GetRect().right + r;
                        vx = -vx;
                    }
                }
            }
            if(vy > 0)
            {
                int dc = (px - game.gameRect.left) / game.tileWith;
                int dr = (dy - game.gameRect.top + r) / game.tileHeight;
                if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(px, dy+r))
                {
                    game.bricks[dr][dc].Hited(damage);
                    dy =  game.bricks[dr][dc].GetRect().top - r;
                    vy = -vy;
                }
                else
                {
                    dc = (dx - game.gameRect.left) / game.tileWith;
                    dr = (dy - game.gameRect.top + r) / game.tileHeight;
                    if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx, dy+r))
                    {
                        game.bricks[dr][dc].Hited(damage);
                        dy =  game.bricks[dr][dc].GetRect().top - r;
                        vy = -vy;
                    }
                }
            }
            else if(vy < 0)
            {
                int dc = (px - game.gameRect.left) / game.tileWith;
                int dr = (dy - game.gameRect.top -r) / game.tileHeight;
                if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(px, dy-r))
                {
                    game.bricks[dr][dc].Hited(damage);
                    dy =  game.bricks[dr][dc].GetRect().bottom + r;
                    vy = -vy;
                }
                else
                {
                    dc = (dx - game.gameRect.left) / game.tileWith;
                    dr = (dy - game.gameRect.top -r) / game.tileHeight;
                    if(dr < game.brickRows && dc < game.brickCols && !game.bricks[dr][dc].broken && game.bricks[dr][dc].GetRect().contains(dx, dy-r))
                    {
                        game.bricks[dr][dc].Hited(damage);
                        dy =  game.bricks[dr][dc].GetRect().bottom + r;
                        vy = -vy;
                    }
                }
            }

            px = dx;
            py = dy;
        }

        void Update() {
            if(game.ballSizeEffect == Game.EFFECT_NORMALBALL) {
                r = 25;
                damage = 2;
            }
            else if(game.ballSizeEffect == Game.EFFECT_BIGBALL) {
                r = 35;
                damage = 4;
            }
            else {
                r = 15;
                damage = 1;
            }
            if(stick) {
                px = game.player.px + game.player.width/2;
                py = game.player.py - r;
            }
            else {
                Move();
            }
        }

    }

    class Item {
        static final int TYPE_INCREASEWIDTH = 0;
        static final int TYPE_DECREASEWIDTH = 1;
        static final int TYPE_SMALLBALL = 2;
        static final int TYPE_BIGBALL = 3;
        static final int TYPE_CLONEBALL = 4;
        static final int TYPENUM = 5;
        Game game;
        int px, py, dy;
        int vy;
        int width, height;
        int type;
        Bitmap bitmap;
        boolean dead;


        Item(Game game, int x, int y, int w, int h, int type) {
            this.game = game;
            width = w-20;
            height = h-20;
            px = x+10;
            py = y+10;
            vy = 10;
            dy = py;
            dead = false;
            this.type = type;
            if(type == TYPE_INCREASEWIDTH)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.item_incresewidth);
            }
            else if(type == TYPE_DECREASEWIDTH)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.item_decresewidth);
            }
            else if(type == TYPE_SMALLBALL)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.item_smallball);
            }
            else if(type == TYPE_BIGBALL)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.item_bigball);
            }
            else if(type == TYPE_CLONEBALL)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.item_cloneball);
            }
        }

        Rect GetBitmapRect()
        {
            return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        Rect GetRect()
        {
            return new Rect(px, py, px + width, py + height);
        }

        void Move()
        {
            dy = py + vy;
            if (vy > 0)
            {
                if (game.player.GetRect().intersect(GetRect()))
                {
                    if(type == TYPE_INCREASEWIDTH)
                    {
                        game.player.IncreseWidth();
                    }
                    else if(type == TYPE_DECREASEWIDTH)
                    {
                        game.player.DecreseWidth();
                    }
                    else if(type == TYPE_SMALLBALL)
                    {
                        game.addEffect(Game.EFFECT_SMALLBALL);
                    }
                    else if(type == TYPE_BIGBALL)
                    {
                        soundPool.play(soundId_itemBigBall, volume, volume, 1, 0, 1f);
                        game.addEffect(Game.EFFECT_BIGBALL);
                    }
                    else if(type == TYPE_CLONEBALL)
                    {
                        game.cloneBalls();
                    }
                    dead = true;
                }
            }
            if(dy + height > drawView.getHeight())
            {
                dead = true;
            }
            py = dy;

        }

        void Update()
        {
            Move();
        }
    }

    class Brick {
        static final int TYPE_NORMAL = 0;
        static final int TYPE_ITEMBRICK = 1;
        static final int TYPE_HARDBRICK = 2;
        Game game;
        int px, py;
        int width, height;
        int hp;
        int type;
        Bitmap bitmap;
        boolean broken;


        Brick(Game game, int x, int y, int w, int h, int type)
        {

            this.game = game;
            width = w - 10;
            height = h - 10;
            px = x + 5;
            py = y + 5;
            this.type = type;
            broken = false;
            if(type == -1)
            {
                broken = true;
                hp = 0;
            }
            else if(type == TYPE_NORMAL)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_normal);
                hp = 2;
            }
            else if(type == TYPE_ITEMBRICK)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_itembrick);
                hp = 2;
            }
            else if(type == TYPE_HARDBRICK)
            {
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_hardbrick);
                hp = 4;
            }
        }

        Rect GetBitmapRect()
        {
            return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        Rect GetRect()
        {
            return new Rect(px, py, px + width, py + height);
        }

        void CloneState(Brick brick) {
            type = brick.type;
            hp = brick.hp;
            broken = brick.broken;
            bitmap = brick.bitmap;

        }

        void Hited(int damage)
        {
            if(!broken)
            {
                hp -= damage;
                if(hp <= 0)
                {
                    soundPool.play(soundId_brickBroken, volume, volume, 1, 0, 1f);
                    game.score += 1;
                    hp = 0;
                    broken = true;
                    game.brickNum--;
                    if(type == TYPE_ITEMBRICK)
                    {
                        game.items.add(new Item(game, px, py, width, height, (int)(Math.random() * Item.TYPENUM)));
                    }
                }
                else
                {
                    soundPool.play(soundId_brickHited, volume, volume, 1, 0, 1f);
                }
                if(type == TYPE_NORMAL)
                {
                    if(hp == 1) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_normal_damaged1);
                }
                else if(type == TYPE_ITEMBRICK)
                {
                    if(hp == 1) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_itembrick_damaged1);
                }
                else if(type == TYPE_HARDBRICK)
                {
                    if(hp == 3) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_hardbrick_damaged1);
                    else if(hp == 2) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_hardbrick_damaged2);
                    else if(hp == 1) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.brick_hardbrick_damaged3);
                }
            }
        }

    }

    class DrawView extends View {
        Paint paint;
        int frameCount;
        boolean drag, initial;
        Rect pauseRect;
        Rect optionMenuRect;
        Rect restartRect;
        Rect resumeRect;
        Rect backToMenuRect;
        Rect exitRect;
        Rect menuStartRect;
        Rect menuChallengeRect;
        Rect menuExitRect;
        public DrawView(Context context)
        {
            super(context);
            paint = new Paint();
            frameCount = 0;
            drag = false;
            initial =false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event)
        {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (gameStart) {
                        if (game.gameState == Game.STATE_PLAYING) {
                            //Pause Click
                            if (pauseRect.contains((int) event.getX(), (int) event.getY())) {
                                game.gameState = Game.STATE_PAUSE;
                            }
                            if (game.player.GetRect().contains((int) event.getX(), (int) event.getY())) {
                                drag = true;
                            } else if (!game.control_Shoot) {
                                game.control_Shoot = true;
                            }
                        } else if (game.gameState == Game.STATE_PAUSE){
                            if (resumeRect.contains((int) event.getX(), (int) event.getY())) {
                                game.gameState = Game.STATE_PLAYING;
                            }
                            if (backToMenuRect.contains((int) event.getX(), (int) event.getY())) {
                                gameStart = false;
                            }
                            if (exitRect.contains((int) event.getX(), (int) event.getY())) {
                                finish();
                                System.exit(0);
                            }
                            if (restartRect.contains((int) event.getX(), (int) event.getY())) {
                                game.Initial();
                            }
                        }
                        if (game.gameState == Game.STATE_GAMEWIN || game.gameState == Game.STATE_GAMEOVER) {
                            gameStart = false;
                        }
                    } else {
                        if (menuStartRect.contains((int) event.getX(), (int) event.getY())) {
                            game = new Game(Game.MODE_NORMAL);
                            gameStart = true;
                        }
                        if (menuChallengeRect.contains((int) event.getX(), (int) event.getY())) {
                            game = new Game(Game.MODE_CHALLENGE);
                            gameStart = true;
                        }
                        if (menuExitRect.contains((int) event.getX(), (int) event.getY())) {
                            finish();
                            System.exit(0);
                        }
                    }
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    drag = false;
                    if (gameStart)
                    {
                        if(game.gameState == Game.STATE_PLAYING)
                        {
                            game.player.control_targetX = game.player.px;
                        }
                    }
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (gameStart)
                    {
                        if(drag)
                        {
                            if(game.gameState == Game.STATE_PLAYING)
                            {
                                game.player.control_targetX = (int) event.getX() - game.player.width / 2;
                            }
                        }
                    }
                    invalidate();
                    break;
            }
            return true;
        }

        protected void onDraw(Canvas canvas)
        {
            if(!initial) {
                pauseRect = new Rect(getWidth() - 60, 10, getWidth() - 10, 60);
                optionMenuRect = new Rect(getWidth() / 2 - 150, getHeight() / 2 - 200, getWidth() / 2 + 150, getHeight() / 2 + 300);
                resumeRect = new Rect(optionMenuRect.left + 20, optionMenuRect.top + 50, optionMenuRect.right - 20, optionMenuRect.top + 150);
                restartRect = new Rect(optionMenuRect.left + 20, optionMenuRect.top + 160, optionMenuRect.right - 20, optionMenuRect.top + 260);
                backToMenuRect = new Rect(optionMenuRect.left + 20, optionMenuRect.top + 270, optionMenuRect.right - 20, optionMenuRect.top + 370);
                exitRect = new Rect(optionMenuRect.left + 20, optionMenuRect.top + 380, optionMenuRect.right - 20, optionMenuRect.top + 480);
                menuStartRect = new Rect(getWidth() / 2 - 150, getHeight() / 2 - 250, getWidth() / 2 + 150, getHeight() / 2 - 150);
                menuChallengeRect = new Rect(getWidth() / 2 - 150, getHeight() / 2 - 100, getWidth() / 2 + 150, getHeight() / 2);
                menuExitRect = new Rect(getWidth() / 2 - 150, getHeight() / 2 + 50, getWidth() / 2 + 150, getHeight() / 2 + 150);
                initial = true;
            }
            if(gameStart)
            {
                paint.setColor(Color.BLACK);
                canvas.drawRect(game.gameRect, paint);
                if(game.gameState == Game.STATE_PLAYING)
                {
                    game.Update();

                }

                //top panel
                //pause
                paint.setColor(Color.BLACK);
                canvas.drawRect(getWidth()-60, 25, getWidth()-40, 75, paint);
                canvas.drawRect(getWidth()-30, 25, getWidth()-10, 75, paint);

                //life
                canvas.drawCircle(35, 50, 25, paint);
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("x" + game.life,70, 50 - paint.getFontMetrics().ascent / 2, paint);

                if(game.gameMode == Game.MODE_CHALLENGE)
                {
                    //level
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText("lv:" + game.gameLevel,350, 50 - paint.getFontMetrics().ascent / 2, paint);
                }

                //score
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("score:" + game.score,500, 50 - paint.getFontMetrics().ascent / 2, paint);

                //effect
                if(game.ballSizeEffect == Game.EFFECT_SMALLBALL) {
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText("S:" + (int)(game.ballSizeEffectTime * 0.03+1) + "s",160, 50 - paint.getFontMetrics().ascent / 2, paint);
                }
                else if(game.ballSizeEffect == Game.EFFECT_BIGBALL) {
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText("B:" + (int)(game.ballSizeEffectTime * 0.03+1) + "s",160, 50 - paint.getFontMetrics().ascent / 2, paint);
                }

                for (int i = 0; i < game.items.size(); i++)
                {
                    canvas.drawBitmap(game.items.elementAt(i).bitmap, game.items.elementAt(i).GetBitmapRect(), game.items.elementAt(i).GetRect(), paint);
                }

                paint.setColor(Color.CYAN);
                canvas.drawRect(game.player.GetRect(), paint);
                if (game.stickedBalls.size() > 0)
                {
                    canvas.drawBitmap(game.stickedBalls.firstElement().bitmap, game.stickedBalls.firstElement().GetBitmapRect(), game.stickedBalls.firstElement().GetRect(), paint);
                }
                for (int i = 0; i < game.balls.size(); i++)
                {
                    canvas.drawBitmap(game.balls.elementAt(i).bitmap, game.balls.elementAt(i).GetBitmapRect(), game.balls.elementAt(i).GetRect(), paint);
                }

                for (int i = 0; i < game.brickRows; i++)
                {
                    for (int j = 0; j < game.brickCols; j++)
                    {
                        if (!game.bricks[i][j].broken)
                        {
                            canvas.drawBitmap(game.bricks[i][j].bitmap, game.bricks[i][j].GetBitmapRect(), game.bricks[i][j].GetRect(), paint);
                        }
                    }
                }

                if(game.gameState == Game.STATE_PAUSE)
                {
                    paint.setColor(Color.GRAY);
                    canvas.drawRect(optionMenuRect, paint);

                    paint.setColor(Color.WHITE);
                    canvas.drawRect(resumeRect, paint);
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("Resume",resumeRect.centerX(), resumeRect.centerY() - paint.getFontMetrics().ascent / 2, paint);

                    paint.setColor(Color.WHITE);
                    canvas.drawRect(restartRect, paint);
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("Restart",restartRect.centerX(), restartRect.centerY() - paint.getFontMetrics().ascent / 2, paint);

                    paint.setColor(Color.WHITE);
                    canvas.drawRect(backToMenuRect, paint);
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("MENU",backToMenuRect.centerX(), backToMenuRect.centerY() - paint.getFontMetrics().ascent / 2, paint);

                    paint.setColor(Color.WHITE);
                    canvas.drawRect(exitRect, paint);
                    paint.setColor(Color.BLACK);
                    paint.setTextSize(60);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("Exit",exitRect.centerX(), exitRect.centerY() - paint.getFontMetrics().ascent / 2, paint);
                }
                else if(game.gameState == Game.STATE_GAMEWIN)
                {
                    if (frameCount % 40 < 30)
                    {
                        paint.setColor(Color.RED);
                        paint.setTextSize(60);
                        paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("WIN",getWidth()/2, getHeight()/2 - paint.getFontMetrics().ascent / 2, paint);
                    }
                }
                else if(game.gameState == Game.STATE_GAMEOVER)
                {
                    if (frameCount % 40 < 30)
                    {
                        paint.setColor(Color.RED);
                        paint.setTextSize(60);
                        paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("OVER",getWidth()/2, getHeight()/2 - paint.getFontMetrics().ascent / 2, paint);
                    }
                }
            }
            else
            {
                paint.setColor(Color.LTGRAY);
                canvas.drawRect(menuStartRect, paint);
                paint.setColor(Color.BLACK);
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Start",menuStartRect.centerX(), menuStartRect.centerY() - paint.getFontMetrics().ascent / 2, paint);

                paint.setColor(Color.LTGRAY);
                canvas.drawRect(menuChallengeRect, paint);
                paint.setColor(Color.BLACK);
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Challenge",menuChallengeRect.centerX(), menuChallengeRect.centerY() - paint.getFontMetrics().ascent / 2, paint);

                paint.setColor(Color.LTGRAY);
                canvas.drawRect(menuExitRect, paint);
                paint.setColor(Color.BLACK);
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Exit",menuExitRect.centerX(), menuExitRect.centerY() - paint.getFontMetrics().ascent / 2, paint);
            }


            frameCount = (frameCount+1) % 10000;
            try {
                Thread.sleep(30);
                invalidate();
            }catch(Exception e) {
                Log.v("error",e.toString());
            }
        }
    }
}