#include "TokenBucket.h"
#include <iostream>
#include <string>
#include <mutex>
#include <chrono>
using std::cin;
using std::cout;
using std::thread;
using high_clock = typename std::chrono::high_resolution_clock;
using miliseconds = typename std::chrono::milliseconds;
using seconds = typename std::chrono::seconds;
using mutex=typename std::mutex;
using lock_guard = typename std::lock_guard<typename mutex>;
using std::chrono::duration_cast;
mutex mut;
void thr1(bool& stop_,size_t milisecond)
{
	tokenbucket& token = tokenbucket::getInstance();
	thread::id current_id = std::this_thread::get_id();
	seconds cur_time;
	while (!stop_)
	{
		std::this_thread::sleep_for(miliseconds(milisecond));
		if (token.acquire())
		{
			lock_guard lock_(mut);
			cur_time = duration_cast<seconds>(high_clock::now().time_since_epoch());
			cout << current_id << ":acquire token in " << cur_time.count() << "\n";
		}
		else
		{			
			lock_guard lock_(mut);
			cur_time = duration_cast<seconds>(high_clock::now().time_since_epoch());
			cout << current_id << ":don't acquire token in " << high_clock::now().time_since_epoch().count() << "\n";
		}
	}
	lock_guard lock_(mut);
	cout << current_id << "quit\n";
}
void thr2(bool& stop_, size_t milisecond)
{
	tokenbucket& token = tokenbucket::getInstance();
	thread::id current_id = std::this_thread::get_id();
	seconds cur_time;
	while (!stop_)
	{
		std::this_thread::sleep_for(miliseconds(milisecond));
		if (token.nonblockacquire())
		{
			lock_guard lock_(mut);
			cur_time = duration_cast<seconds>(high_clock::now().time_since_epoch());
			cout << current_id << ":acquire token in " << cur_time.count() << "\n";
		}
		else
		{
			
			lock_guard lock_(mut);
			cur_time = duration_cast<seconds>(high_clock::now().time_since_epoch());
			cout << current_id << ":don't acquire token in " << cur_time.count() << "\n";
		}
	}
	lock_guard lock_(mut);
	cout << current_id << "quit\n";
}
int main(void)
{
	bool stop_ = false;
	//每200ms采用阻塞方式请求一次令牌
	thread t1(thr1,std::ref(stop_),200);
	//每20ms采用非阻塞方式请求一次令牌
	thread t2(thr2, std::ref(stop_), 20);
	//每100ms采用阻塞方式请求一次令牌
	thread t3(thr1, std::ref(stop_), 100);
	//运行1s
	std::this_thread::sleep_for(miliseconds(1000));
	stop_ = true;
	tokenbucket::getInstance().quit();
	t1.join();
	t2.join();
	t3.join();
	cout << "quit\n";
	return 0;
}