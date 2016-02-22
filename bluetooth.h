#ifndef bluetooth_h
#define bluetooth_h



class bluetooth{
  public:
    void SETUP();
    bool bt_read();
    void bt_write();  
};

extern bluetooth bt;

#endif